package com.venkoi.terminal.licensing

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceIdentity(val deviceKeyId: String, val deviceCode: String)

@Singleton
class DeviceIdentityProvider @Inject constructor() {
    private val alias = "sales_terminal_device_identity_v1"

    @Synchronized
    fun get(): DeviceIdentity {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build()
                )
            }.generateKeyPair()
        }
        val publicBytes = store.getCertificate(alias).publicKey.encoded
        val hash = MessageDigest.getInstance("SHA-256").digest(publicBytes)
        val keyId = Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
        val code = hash.copyOfRange(0, 16).joinToString("") { "%02X".format(it) }
            .chunked(4).joinToString("-")
        return DeviceIdentity(keyId, code)
    }
}
