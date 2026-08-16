package com.venkoi.terminal.licensing

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.inject.Inject
import javax.inject.Singleton

data class TrustedTimeResult(val now: Instant, val error: LicenseState? = null)

@Singleton
class TrustedTimeStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("license_security_v1", Context.MODE_PRIVATE)
    private val alias = "sales_terminal_trusted_time_hmac_v1"
    private val tolerance = Duration.ofMinutes(5)
    private val sessionWall = Instant.now()
    private val sessionElapsed = SystemClock.elapsedRealtime()

    @Synchronized
    fun observe(wallNow: Instant = Instant.now(), elapsedNow: Long = SystemClock.elapsedRealtime()): TrustedTimeResult {
        val stored = readAuthenticated()
        if (stored == Read.Invalid) return TrustedTimeResult(wallNow, LicenseState.LOCAL_SECURITY_STATE_INVALID)
        val previous = (stored as? Read.Valid)?.instant
        val sessionFloor = sessionWall.plusMillis((elapsedNow - sessionElapsed).coerceAtLeast(0))
        val floor = listOfNotNull(previous, sessionFloor).maxOrNull() ?: wallNow
        if (wallNow.plus(tolerance).isBefore(floor)) {
            return TrustedTimeResult(floor, LicenseState.CLOCK_ROLLBACK_DETECTED)
        }
        val trusted = maxOf(wallNow, floor)
        persist(trusted)
        return TrustedTimeResult(trusted)
    }

    @Synchronized
    fun advanceFloor(floor: Instant) {
        val current = (readAuthenticated() as? Read.Valid)?.instant
        persist(if (current == null) floor else maxOf(current, floor))
    }

    private sealed interface Read { data object Missing : Read; data object Invalid : Read; data class Valid(val instant: Instant) : Read }

    private fun readAuthenticated(): Read {
        val payload = preferences.getString("payload", null) ?: return Read.Missing
        val encodedMac = preferences.getString("mac", null) ?: return Read.Invalid
        val expected = runCatching { Base64.getDecoder().decode(encodedMac) }.getOrNull() ?: return Read.Invalid
        if (!java.security.MessageDigest.isEqual(mac(payload), expected)) return Read.Invalid
        return runCatching { Read.Valid(Instant.parse(payload)) }.getOrElse { Read.Invalid }
    }

    private fun persist(instant: Instant) {
        val payload = instant.toString()
        preferences.edit().putString("payload", payload)
            .putString("mac", Base64.getEncoder().encodeToString(mac(payload))).commit()
    }

    private fun mac(payload: String): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(hmacKey())
        doFinal(payload.toByteArray(StandardCharsets.UTF_8))
    }

    private fun hmacKey(): java.security.Key {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.getKey(alias, null)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256).build())
            generateKey()
        }
    }
}
