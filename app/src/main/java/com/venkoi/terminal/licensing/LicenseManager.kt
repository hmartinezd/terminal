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
    }

    fun requireSelling() {
        if (runtimePolicy.developerAuthorization) return
        val result = LicensePolicy.sellingAuthorization(_snapshot.value.state)
        if (result != SellingAuthorizationResult.AUTHORIZED && result != SellingAuthorizationResult.AUTHORIZED_GRACE) {
            throw SellingNotAuthorizedException(result)
        }
    }

    suspend fun refresh() {
        val terminal = terminalRepository.getConfiguration()
        val restaurant = menuRepository.getRestaurantConfiguration()
        if (terminal == null || restaurant == null) {
            _snapshot.value = LicenseSnapshot(LicenseState.NOT_ACTIVATED)
            return
        }
        val license = storedLicense()
        val time = trustedTime.observe(clock.now())
        _snapshot.value = EvaluateLicense.evaluate(
            license, license?.let(verifier::verify) ?: false,
            restaurant.restaurantId, terminal.terminalId.value, deviceIdentity.deviceKeyId,
            time.now, time.error, runtimePolicy.appIntegrityValid()
        )
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

    suspend fun import(raw: String): LicenseImportResult {
        val candidate = runCatching { json.decodeFromString<SignedLicenseV1>(raw) }.getOrNull()
            ?: return LicenseImportResult.Malformed
        val terminal = terminalRepository.getConfiguration() ?: return LicenseImportResult.Malformed
        val restaurant = menuRepository.getRestaurantConfiguration() ?: return LicenseImportResult.Malformed
        val evaluation = EvaluateLicense.evaluate(
            candidate, verifier.verify(candidate), restaurant.restaurantId, terminal.terminalId.value,
            deviceIdentity.deviceKeyId, trustedTime.observe(clock.now()).now,
            appIntegrityValid = true // import validates license identity; runtime integrity is evaluated separately
        )
        if (evaluation.state !in setOf(LicenseState.VALID, LicenseState.EXPIRING_SOON, LicenseState.GRACE_PERIOD, LicenseState.EXPIRED)) {
            return LicenseImportResult.Rejected(evaluation.state)
        }
        val current = storedLicense()
        if (current != null) {
            if (candidate.payload.licenseSequence < current.payload.licenseSequence) return LicenseImportResult.Stale
            if (candidate.payload.licenseSequence == current.payload.licenseSequence) {
                return if (CanonicalLicenseEncoder.encode(candidate.payload).contentEquals(CanonicalLicenseEncoder.encode(current.payload)) &&
                    candidate.signatureBase64Url == current.signatureBase64Url) LicenseImportResult.Duplicate
                else LicenseImportResult.SequenceConflict
            }
        }
        preferences.edit().putString("envelope", json.encodeToString(candidate))
            .putLong("imported_at", clock.now().toEpochMilli()).commit()
        trustedTime.advanceFloor(candidate.payload.issuedAt())
        refresh()
        return LicenseImportResult.Accepted
    }

    private fun storedLicense(): SignedLicenseV1? = preferences.getString("envelope", null)?.let {
        runCatching { json.decodeFromString<SignedLicenseV1>(it) }.getOrNull()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object LicenseModule {
    @Provides @Singleton fun authorityProvider(): LicenseAuthorityPublicKeyProvider =
        EncodedLicenseAuthorityPublicKeyProvider(BuildConfig.LICENSE_AUTHORITY_PUBLIC_KEY)

    @Provides @Singleton fun verifier(provider: LicenseAuthorityPublicKeyProvider) = LicenseSignatureVerifier(provider)
}
