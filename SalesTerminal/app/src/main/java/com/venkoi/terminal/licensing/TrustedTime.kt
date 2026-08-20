package com.venkoi.terminal.licensing

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.inject.Inject
import javax.inject.Singleton

data class TrustedTimeResult(val now: Instant, val error: LicenseState? = null)

data class LicenseSecurityStateV1(
    val schemaVersion: Int = 1,
    val lastTrustedUtc: Instant,
    val highestAcceptedLicenseSequence: Long = 0
)

fun interface WallTimeSource { fun now(): Instant }
fun interface ElapsedRealtimeSource { fun nowMillis(): Long }

@Singleton class SystemWallTimeSource @Inject constructor() : WallTimeSource {
    override fun now(): Instant = Instant.now()
}

@Singleton class SystemElapsedRealtimeSource @Inject constructor() : ElapsedRealtimeSource {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}

internal interface SecurityStatePersistence {
    fun payload(): String?
    fun mac(): String?
    fun write(payload: String, mac: String)
}

internal interface SecurityStateAuthenticator {
    fun keyExists(): Boolean
    fun createKey()
    fun mac(payload: String): ByteArray
}

private class PreferencesSecurityStatePersistence(context: Context) : SecurityStatePersistence {
    private val preferences = context.getSharedPreferences("license_security_v1", Context.MODE_PRIVATE)
    override fun payload(): String? = preferences.getString("payload", null)
    override fun mac(): String? = preferences.getString("mac", null)
    override fun write(payload: String, mac: String) {
        check(preferences.edit().putString("payload", payload).putString("mac", mac).commit())
    }
}

private class KeystoreSecurityStateAuthenticator : SecurityStateAuthenticator {
    private val alias = "sales_terminal_trusted_time_hmac_v1"
    override fun keyExists(): Boolean = keyStore().containsAlias(alias)
    override fun createKey() {
        if (keyExists()) return
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256).build())
            generateKey()
        }
    }
    override fun mac(payload: String): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(keyStore().getKey(alias, null) ?: error("License security key is missing"))
        doFinal(payload.toByteArray(StandardCharsets.UTF_8))
    }
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}

@Singleton
class TrustedTimeStore private constructor(
    private val persistence: SecurityStatePersistence,
    private val authenticator: SecurityStateAuthenticator,
    private val wallTimeSource: WallTimeSource,
    private val elapsedRealtimeSource: ElapsedRealtimeSource
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        wallTimeSource: SystemWallTimeSource,
        elapsedRealtimeSource: SystemElapsedRealtimeSource
    ) : this(PreferencesSecurityStatePersistence(context), KeystoreSecurityStateAuthenticator(), wallTimeSource, elapsedRealtimeSource)

    internal constructor(
        persistence: SecurityStatePersistence,
        authenticator: SecurityStateAuthenticator,
        wallTimeSource: WallTimeSource,
        elapsedRealtimeSource: ElapsedRealtimeSource,
        @Suppress("UNUSED_PARAMETER") testOnly: Unit = Unit
    ) : this(persistence, authenticator, wallTimeSource, elapsedRealtimeSource)

    private val tolerance = Duration.ofMinutes(5)
    private var sessionWall = wallTimeSource.now()
    private var sessionElapsed = elapsedRealtimeSource.nowMillis()

    @Synchronized
    fun observe(wallNow: Instant = wallTimeSource.now(), elapsedNow: Long = elapsedRealtimeSource.nowMillis()): TrustedTimeResult {
        val stored = readAuthenticated()
        if (stored == Read.Invalid) return TrustedTimeResult(wallNow, LicenseState.LOCAL_SECURITY_STATE_INVALID)
        val previous = (stored as? Read.Valid)?.state
        val sessionFloor = sessionWall.plusMillis((elapsedNow - sessionElapsed).coerceAtLeast(0))
        val floor = listOfNotNull(previous?.lastTrustedUtc, sessionFloor).maxOrNull() ?: wallNow
        if (wallNow.plus(tolerance).isBefore(floor)) return TrustedTimeResult(floor, LicenseState.CLOCK_ROLLBACK_DETECTED)
        val trusted = maxOf(wallNow, floor)
        persist(LicenseSecurityStateV1(lastTrustedUtc = trusted,
            highestAcceptedLicenseSequence = previous?.highestAcceptedLicenseSequence ?: 0), stored == Read.Fresh)
        return TrustedTimeResult(trusted)
    }

    @Synchronized fun securityState(): LicenseSecurityStateV1? = (readAuthenticated() as? Read.Valid)?.state
    @Synchronized fun securityStateError(): LicenseState? =
        if (readAuthenticated() == Read.Invalid) LicenseState.LOCAL_SECURITY_STATE_INVALID else null

    @Synchronized
    fun acceptLicense(issuedAt: Instant, sequence: Long) {
        val read = readAuthenticated()
        check(read != Read.Invalid) { "Local license security state is invalid" }
        val current = (read as? Read.Valid)?.state
        persist(LicenseSecurityStateV1(
            lastTrustedUtc = maxOf(current?.lastTrustedUtc ?: issuedAt, issuedAt),
            highestAcceptedLicenseSequence = maxOf(current?.highestAcceptedLicenseSequence ?: 0, sequence)
        ), read == Read.Fresh)
    }

    /** The sole operation allowed to lower the authenticated time floor. */
    @Synchronized
    fun reanchorAfterAuthorizedClockCorrection(
        correctedWallNow: Instant,
        candidateIssuedAt: Instant,
        candidateSequence: Long,
        elapsedNow: Long = elapsedRealtimeSource.nowMillis()
    ): Boolean {
        val read = readAuthenticated()
        val current = (read as? Read.Valid)?.state ?: return false
        val sessionFloor = sessionWall.plusMillis((elapsedNow - sessionElapsed).coerceAtLeast(0))
        val trustedFloor = maxOf(current.lastTrustedUtc, sessionFloor)
        if (!correctedWallNow.plus(tolerance).isBefore(trustedFloor)) return false
        if (candidateSequence <= current.highestAcceptedLicenseSequence) return false
        if (correctedWallNow.plus(tolerance).isBefore(candidateIssuedAt)) return false

        val recovered = LicenseSecurityStateV1(
            lastTrustedUtc = maxOf(correctedWallNow, candidateIssuedAt),
            highestAcceptedLicenseSequence = candidateSequence
        )
        return try {
            persist(recovered, initialize = false)
            sessionWall = recovered.lastTrustedUtc
            sessionElapsed = elapsedNow
            true
        } catch (_: Throwable) {
            // Best-effort rollback if a persistence implementation failed after a partial write.
            runCatching { persist(current, initialize = false) }
            false
        }
    }

    @Synchronized
    fun advanceFloor(floor: Instant) {
        val read = readAuthenticated()
        check(read != Read.Invalid) { "Local license security state is invalid" }
        val current = (read as? Read.Valid)?.state
        persist(LicenseSecurityStateV1(
            lastTrustedUtc = maxOf(current?.lastTrustedUtc ?: floor, floor),
            highestAcceptedLicenseSequence = current?.highestAcceptedLicenseSequence ?: 0
        ), read == Read.Fresh)
    }

    private sealed interface Read {
        data object Fresh : Read
        data object Invalid : Read
        data class Valid(val state: LicenseSecurityStateV1) : Read
    }

    private fun readAuthenticated(): Read {
        val payload = persistence.payload()
        val encodedMac = persistence.mac()
        val keyExists = runCatching { authenticator.keyExists() }.getOrDefault(false)
        if (payload == null && encodedMac == null) return if (keyExists) Read.Invalid else Read.Fresh
        if (payload == null || encodedMac == null || !keyExists) return Read.Invalid
        val expected = runCatching { Base64.getDecoder().decode(encodedMac) }.getOrNull() ?: return Read.Invalid
        val actual = runCatching { authenticator.mac(payload) }.getOrNull() ?: return Read.Invalid
        if (!MessageDigest.isEqual(actual, expected)) return Read.Invalid
        return parse(payload)?.let(Read::Valid) ?: Read.Invalid
    }

    private fun persist(state: LicenseSecurityStateV1, initialize: Boolean) {
        if (initialize) authenticator.createKey()
        val payload = serialize(state)
        persistence.write(payload, Base64.getEncoder().encodeToString(authenticator.mac(payload)))
    }

    private fun serialize(state: LicenseSecurityStateV1): String = listOf(
        state.schemaVersion.toString(), state.lastTrustedUtc.toString(), state.highestAcceptedLicenseSequence.toString()
    ).joinToString("\n")

    private fun parse(payload: String): LicenseSecurityStateV1? {
        val fields = payload.split('\n')
        // M8 authenticated only the instant. A valid legacy MAC permits a one-way migration;
        // the verified installed envelope supplies the sequence floor during the next import.
        if (fields.size == 1) return runCatching {
            LicenseSecurityStateV1(lastTrustedUtc = Instant.parse(payload))
        }.getOrNull()
        if (fields.size != 3 || fields[0] != "1") return null
        return runCatching { LicenseSecurityStateV1(fields[0].toInt(), Instant.parse(fields[1]),
            fields[2].toLong().also { require(it >= 0) }) }.getOrNull()
    }
}
