package com.venkoi.terminal.licensing

import android.content.Context
import com.venkoi.terminal.BuildConfig
import com.venkoi.terminal.core.Clock
import com.venkoi.terminal.core.IdGenerator
import com.venkoi.terminal.domain.repository.MenuRepository
import com.venkoi.terminal.domain.repository.TerminalConfigurationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LicenseImportResult {
    data object Accepted : LicenseImportResult
    data object Duplicate : LicenseImportResult
    data object Stale : LicenseImportResult
    data object SequenceConflict : LicenseImportResult
    data class Rejected(val state: LicenseState) : LicenseImportResult
    data object Malformed : LicenseImportResult
}

@Singleton
class LicenseManager @Inject constructor(
    @ApplicationContext context: Context,
    private val terminalRepository: TerminalConfigurationRepository,
    private val menuRepository: MenuRepository,
    private val identityProvider: DeviceIdentityProvider,
    private val trustedTime: TrustedTimeStore,
    private val verifier: LicenseSignatureVerifier,
    private val runtimePolicy: RuntimeLicensePolicy,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val json: Json
) {
    private val preferences = context.getSharedPreferences("installed_license_v1", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _snapshot = MutableStateFlow(LicenseSnapshot(LicenseState.NOT_ACTIVATED))
    private val evaluationMutex = Mutex()
    private val importMutex = Mutex()
    val snapshot: StateFlow<LicenseSnapshot> = _snapshot
    val sellingAllowed: StateFlow<Boolean> = _snapshot.map {
        runtimePolicy.developerAuthorization || LicensePolicy.sellingAuthorization(it.state) in
            setOf(SellingAuthorizationResult.AUTHORIZED, SellingAuthorizationResult.AUTHORIZED_GRACE)
    }.stateIn(scope, SharingStarted.Eagerly, runtimePolicy.developerAuthorization)
    val deviceIdentity: DeviceIdentity by lazy { identityProvider.get() }

    init {
        scope.launch {
            combine(terminalRepository.observeConfiguration(), menuRepository.observeRestaurantConfiguration()) { _, _ -> Unit }
                .collect { refresh() }
        }
        scope.launch {
            while (true) {
                delay(60_000)
                refresh()
            }
        }
    }

    suspend fun requireSelling() {
        if (runtimePolicy.developerAuthorization) return
        CurrentLicenseAuthorizer.requireAuthorized { evaluateCurrentLicense() }
    }

    suspend fun refresh() { evaluateCurrentLicense() }

    suspend fun evaluateCurrentLicense(): LicenseSnapshot = evaluationMutex.withLock {
        val terminal = terminalRepository.getConfiguration()
        val restaurant = menuRepository.getRestaurantConfiguration()
        if (terminal == null || restaurant == null) {
            return@withLock LicenseSnapshot(LicenseState.NOT_ACTIVATED).also { _snapshot.value = it }
        }
        val license = storedLicense()
        val time = trustedTime.observe(clock.now())
        EvaluateLicense.evaluate(
            license, license?.let(verifier::verify) ?: false,
            restaurant.restaurantId, terminal.terminalId.value, deviceIdentity.deviceKeyId,
            time.now, time.error, runCatching { runtimePolicy.appIntegrityValid() }.getOrDefault(false)
        ).also { _snapshot.value = it }
    }

    suspend fun activationRequest(): ActivationRequestV1 {
        val terminal = terminalRepository.getConfiguration() ?: error("Terminal not configured")
        val restaurant = menuRepository.getRestaurantConfiguration() ?: error("Restaurant not configured")
        return ActivationRequestV1(
            restaurantId = restaurant.restaurantId,
            terminalId = terminal.terminalId.value,
            deviceKeyId = deviceIdentity.deviceKeyId,
            generatedAtDeviceUtc = clock.now().toString(),
            requestId = idGenerator.nextId()
        )
    }

    suspend fun import(raw: String): LicenseImportResult = importMutex.withLock {
        val candidate = runCatching { json.decodeFromString<SignedLicenseV1>(raw) }.getOrNull()
            ?: return LicenseImportResult.Malformed
        val terminal = terminalRepository.getConfiguration() ?: return LicenseImportResult.Malformed
        val restaurant = menuRepository.getRestaurantConfiguration() ?: return LicenseImportResult.Malformed
        val wallNow = clock.now()
        val timeObservation = trustedTime.observe(wallNow)
        val recoveringClock = timeObservation.error == LicenseState.CLOCK_ROLLBACK_DETECTED
        val evaluation = EvaluateLicense.evaluate(
            candidate, verifier.verify(candidate), restaurant.restaurantId, terminal.terminalId.value,
            deviceIdentity.deviceKeyId, if (recoveringClock) wallNow else timeObservation.now,
            appIntegrityValid = true // import validates license identity; runtime integrity is evaluated separately
        )
        val normallyImportable = setOf(LicenseState.VALID, LicenseState.EXPIRING_SOON, LicenseState.GRACE_PERIOD, LicenseState.EXPIRED)
        val recoveryCapable = setOf(LicenseState.VALID, LicenseState.EXPIRING_SOON, LicenseState.GRACE_PERIOD)
        if (evaluation.state !in if (recoveringClock) recoveryCapable else normallyImportable) {
            return LicenseImportResult.Rejected(evaluation.state)
        }
        if (trustedTime.securityStateError() != null) {
            return LicenseImportResult.Rejected(LicenseState.LOCAL_SECURITY_STATE_INVALID)
        }
        val current = storedLicense()?.takeIf(verifier::verify)
        val authenticatedHighest = trustedTime.securityState()?.highestAcceptedLicenseSequence ?: 0
        val resumesInterruptedRecovery = LicenseImportRules.canResumeInterruptedRecovery(
            recoveringClock, candidate, current, authenticatedHighest
        )
        val highest = maxOf(authenticatedHighest, current?.payload?.licenseSequence ?: 0)
        if (!resumesInterruptedRecovery) {
            when (LicenseImportRules.compare(candidate, current, highest)) {
                LicenseImportDecision.STALE -> return LicenseImportResult.Stale
                LicenseImportDecision.DUPLICATE -> return LicenseImportResult.Duplicate
                LicenseImportDecision.SEQUENCE_CONFLICT -> return LicenseImportResult.SequenceConflict
                LicenseImportDecision.LOCAL_STATE_INVALID ->
                    return LicenseImportResult.Rejected(LicenseState.LOCAL_SECURITY_STATE_INVALID)
                LicenseImportDecision.ACCEPT -> Unit
            }
        }
        if (recoveringClock && wallNow.plus(Duration.ofMinutes(5)).isBefore(candidate.payload.issuedAt())) {
            return LicenseImportResult.Rejected(LicenseState.CLOCK_ROLLBACK_DETECTED)
        }
        val previousEnvelope = preferences.getString("envelope", null)
        val previousImportedAt = preferences.getLong("imported_at", Long.MIN_VALUE)
        if (!resumesInterruptedRecovery) {
            val stored = preferences.edit().putString("envelope", json.encodeToString(candidate))
                .putLong("imported_at", wallNow.toEpochMilli()).commit()
            if (!stored) return LicenseImportResult.Rejected(LicenseState.LOCAL_SECURITY_STATE_INVALID)
        }
        val timeAccepted = if (recoveringClock) {
            trustedTime.reanchorAfterAuthorizedClockCorrection(
                wallNow, candidate.payload.issuedAt(), candidate.payload.licenseSequence
            )
        } else runCatching {
            trustedTime.acceptLicense(candidate.payload.issuedAt(), candidate.payload.licenseSequence)
        }.isSuccess
        if (!timeAccepted) {
            if (!resumesInterruptedRecovery) {
                preferences.edit().apply {
                    if (previousEnvelope == null) remove("envelope") else putString("envelope", previousEnvelope)
                    if (previousImportedAt == Long.MIN_VALUE) remove("imported_at") else putLong("imported_at", previousImportedAt)
                }.commit()
            }
            return LicenseImportResult.Rejected(LicenseState.LOCAL_SECURITY_STATE_INVALID)
        }
        refresh()
        return LicenseImportResult.Accepted
    }

    private fun storedLicense(): SignedLicenseV1? = preferences.getString("envelope", null)?.let {
        runCatching { json.decodeFromString<SignedLicenseV1>(it) }.getOrNull()
    }
}

internal enum class LicenseImportDecision { ACCEPT, STALE, DUPLICATE, SEQUENCE_CONFLICT, LOCAL_STATE_INVALID }

internal object LicenseImportRules {
    fun isExactPayload(first: SignedLicenseV1, second: SignedLicenseV1): Boolean =
        CanonicalLicenseEncoder.encode(first.payload)
            .contentEquals(CanonicalLicenseEncoder.encode(second.payload))

    fun canResumeInterruptedRecovery(
        recoveringClock: Boolean,
        candidate: SignedLicenseV1,
        verifiedCurrent: SignedLicenseV1?,
        authenticatedHighest: Long
    ): Boolean = recoveringClock && verifiedCurrent != null &&
        isExactPayload(candidate, verifiedCurrent) &&
        candidate.payload.licenseSequence == verifiedCurrent.payload.licenseSequence &&
        candidate.payload.licenseSequence > authenticatedHighest

    fun compare(candidate: SignedLicenseV1, verifiedCurrent: SignedLicenseV1?, highestAccepted: Long): LicenseImportDecision {
        val sequence = candidate.payload.licenseSequence
        if (sequence < highestAccepted) return LicenseImportDecision.STALE
        if (sequence > highestAccepted) return LicenseImportDecision.ACCEPT
        if (verifiedCurrent == null) return LicenseImportDecision.LOCAL_STATE_INVALID
        return if (isExactPayload(candidate, verifiedCurrent)) {
            LicenseImportDecision.DUPLICATE
        } else {
            LicenseImportDecision.SEQUENCE_CONFLICT
        }
    }
}

internal object CurrentLicenseAuthorizer {
    suspend fun requireAuthorized(evaluateNow: suspend () -> LicenseSnapshot) {
        val result = LicensePolicy.sellingAuthorization(evaluateNow().state)
        if (result != SellingAuthorizationResult.AUTHORIZED && result != SellingAuthorizationResult.AUTHORIZED_GRACE) {
            throw SellingNotAuthorizedException(result)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object LicenseModule {
    @Provides @Singleton fun authorityProvider(): LicenseAuthorityPublicKeyProvider =
        EncodedLicenseAuthorityPublicKeyProvider(BuildConfig.LICENSE_AUTHORITY_PUBLIC_KEY)

    @Provides @Singleton fun verifier(provider: LicenseAuthorityPublicKeyProvider) = LicenseSignatureVerifier(provider)
}
