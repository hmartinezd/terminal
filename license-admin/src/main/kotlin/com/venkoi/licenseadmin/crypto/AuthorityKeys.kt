package com.venkoi.licenseadmin.crypto

import com.venkoi.terminal.licensing.CanonicalLicenseEncoder
import com.venkoi.terminal.licensing.LicensePayloadV1
import com.venkoi.terminal.licensing.SignedLicenseV1
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class AuthorityKeyPair(val privateKey: PrivateKey, val publicKey: PublicKey)

object AuthorityKeys {
    private fun decodePem(path: Path, type: String): ByteArray {
        val text = Files.readString(path)
        require(text.contains("-----BEGIN $type-----") && text.contains("-----END $type-----")) {
            "$path is not a $type PEM"
        }
        return Base64.getMimeDecoder().decode(text.replace("-----BEGIN $type-----", "")
            .replace("-----END $type-----", "").replace("\\s".toRegex(), ""))
    }

    fun privateKey(path: Path): PrivateKey = KeyFactory.getInstance("EC")
        .generatePrivate(PKCS8EncodedKeySpec(decodePem(path, "PRIVATE KEY")))

    fun publicKey(path: Path): PublicKey = KeyFactory.getInstance("EC")
        .generatePublic(X509EncodedKeySpec(decodePem(path, "PUBLIC KEY")))

    fun loadAndValidate(privatePath: Path, publicPath: Path): AuthorityKeyPair {
        val pair = AuthorityKeyPair(privateKey(privatePath), publicKey(publicPath))
        val challenge = ByteArray(32).also(SecureRandom()::nextBytes)
        val proof = Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.privateKey); update(challenge); sign()
        }
        val matches = Signature.getInstance("SHA256withECDSA").run {
            initVerify(pair.publicKey); update(challenge); verify(proof)
        }
        require(matches) { "Supplied authority public key does not match the private key" }
        return pair
    }

    fun generate(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
    }.generateKeyPair()

    fun sign(payload: LicensePayloadV1, privateKey: PrivateKey): SignedLicenseV1 {
        val bytes = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey); update(CanonicalLicenseEncoder.encode(payload)); sign()
        }
        return SignedLicenseV1(payload, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
    }

    fun verify(license: SignedLicenseV1, publicKey: PublicKey): Boolean = runCatching {
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(CanonicalLicenseEncoder.encode(license.payload))
            verify(Base64.getUrlDecoder().decode(license.signatureBase64Url))
        }
    }.getOrDefault(false)

    fun fingerprint(key: PublicKey): String = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        .joinToString(":") { "%02X".format(it) }
    fun base64(key: PublicKey): String = Base64.getEncoder().encodeToString(key.encoded)
    fun pem(type: String, bytes: ByteArray): String = "-----BEGIN $type-----\n" +
        Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(bytes) + "\n-----END $type-----\n"
}
