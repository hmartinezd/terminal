package com.venkoi.terminal.issuer

import com.venkoi.terminal.licensing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.*
import java.security.interfaces.ECPrivateKey
import java.security.spec.*
import java.time.Clock
import java.time.Instant
import java.util.*

object JsonWire { val json = Json { prettyPrint = true; ignoreUnknownKeys = false } }
private val auditJson = Json { ignoreUnknownKeys = false }

object ContractValidation {
    fun request(request: ActivationRequestV1): ActivationRequestV1 {
        require(request.schemaVersion == 1) { "Unsupported activation schemaVersion" }
        require(request.productCode == LICENSE_PRODUCT_CODE) { "Wrong activation productCode" }
        require(request.restaurantId.isNotBlank() && request.terminalId.isNotBlank() &&
            request.deviceKeyId.isNotBlank() && request.requestId.isNotBlank()) { "Activation identity fields must be nonblank" }
        Instant.parse(request.generatedAtDeviceUtc)
        return request
    }

    fun payload(payload: LicensePayloadV1): LicensePayloadV1 {
        require(payload.schemaVersion == 1 && payload.productCode == LICENSE_PRODUCT_CODE) { "Invalid license contract" }
        require(payload.licenseId.isNotBlank() && payload.restaurantId.isNotBlank() &&
            payload.terminalId.isNotBlank() && payload.deviceKeyId.isNotBlank() && payload.planCode.isNotBlank()) { "License fields must be nonblank" }
        require(payload.licenseSequence > 0) { "Sequence must be positive" }
        val issued = payload.issuedAt(); val expires = payload.expiresAt(); val grace = payload.graceUntil()
        require(issued < expires) { "Expiration must be after issuance" }
        require(grace >= expires) { "Grace must not precede expiration" }
        return payload
    }
}

object AuthorityKeys {
    private fun pem(path: Path, type: String): ByteArray = Base64.getMimeDecoder().decode(
        Files.readString(path).replace("-----BEGIN $type-----", "")
            .replace("-----END $type-----", "").replace("\\s".toRegex(), ""))
    fun privateKey(path: Path): PrivateKey = KeyFactory.getInstance("EC")
        .generatePrivate(PKCS8EncodedKeySpec(pem(path, "PRIVATE KEY")))
    fun publicKey(path: Path): PublicKey = KeyFactory.getInstance("EC")
        .generatePublic(X509EncodedKeySpec(pem(path, "PUBLIC KEY")))
    fun derivedPublic(privateKey: PrivateKey): PublicKey {
        val key = privateKey as? ECPrivateKey ?: error("Not an EC private key")
        val params = key.params
        val point = multiply(params.generator, key.s, params.curve)
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params))
    }
    private fun multiply(g: ECPoint, scalar: BigInteger, curve: EllipticCurve): ECPoint {
        val p = (curve.field as ECFieldFp).p
        fun add(a: ECPoint?, b: ECPoint?): ECPoint? {
            if (a == null) return b; if (b == null) return a
            if (a.affineX == b.affineX && a.affineY.add(b.affineY).mod(p) == BigInteger.ZERO) return null
            val slope = if (a == b) a.affineX.pow(2).multiply(BigInteger.valueOf(3)).add(curve.a)
                .multiply(a.affineY.multiply(BigInteger.TWO).modInverse(p)).mod(p)
            else b.affineY.subtract(a.affineY).multiply(b.affineX.subtract(a.affineX).mod(p).modInverse(p)).mod(p)
            val x = slope.pow(2).subtract(a.affineX).subtract(b.affineX).mod(p)
            return ECPoint(x, slope.multiply(a.affineX.subtract(x)).subtract(a.affineY).mod(p))
        }
        var n = scalar; var result: ECPoint? = null; var addend: ECPoint? = g
        while (n.signum() > 0) { if (n.testBit(0)) result = add(result, addend); addend = add(addend, addend); n = n.shiftRight(1) }
        return requireNotNull(result)
    }
    fun sign(payload: LicensePayloadV1, key: PrivateKey): SignedLicenseV1 {
        val signer = Signature.getInstance("SHA256withECDSA").apply { initSign(key); update(CanonicalLicenseEncoder.encode(payload)) }
        return SignedLicenseV1(payload, Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()))
    }
    fun verify(license: SignedLicenseV1, key: PublicKey): Boolean = runCatching {
        Signature.getInstance("SHA256withECDSA").apply { initVerify(key); update(CanonicalLicenseEncoder.encode(license.payload)) }
            .verify(Base64.getUrlDecoder().decode(license.signatureBase64Url))
    }.getOrDefault(false)
    fun fingerprint(key: PublicKey): String = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        .joinToString(":") { "%02X".format(it) }
    fun base64(key: PublicKey): String = Base64.getEncoder().encodeToString(key.encoded)
    fun pem(type: String, bytes: ByteArray): String = "-----BEGIN $type-----\n" +
        Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(bytes) + "\n-----END $type-----\n"
}

data class IssueSpec(val plan: String, val sequence: Long, val expires: Instant, val grace: Instant,
    val allowExpiredForTesting: Boolean = false)

class LicenseIssuerService(private val clock: Clock = Clock.systemUTC()) {
    fun issue(request: ActivationRequestV1, spec: IssueSpec, key: PrivateKey): SignedLicenseV1 {
        ContractValidation.request(request); require(spec.sequence > 0) { "Sequence must be positive" }
        val now = clock.instant(); require(spec.allowExpiredForTesting || spec.expires > now) { "Expiration is in the past" }
        val payload = LicensePayloadV1(productCode = LICENSE_PRODUCT_CODE, licenseId = UUID.randomUUID().toString(),
            licenseSequence = spec.sequence, restaurantId = request.restaurantId, terminalId = request.terminalId,
            deviceKeyId = request.deviceKeyId, planCode = spec.plan, issuedAtUtc = now.toString(),
            expiresAtUtc = spec.expires.toString(), graceUntilUtc = spec.grace.toString())
        ContractValidation.payload(payload)
        return AuthorityKeys.sign(payload, key).also { require(AuthorityKeys.verify(it, AuthorityKeys.derivedPublic(key))) { "Self-verification failed" } }
    }
    fun renew(existing: SignedLicenseV1, request: ActivationRequestV1?, spec: IssueSpec, key: PrivateKey): SignedLicenseV1 {
        ContractValidation.payload(existing.payload)
        require(AuthorityKeys.verify(existing, AuthorityKeys.derivedPublic(key))) { "Existing license signature is invalid for this authority" }
        request?.let { ContractValidation.request(it); require(listOf(it.restaurantId, it.terminalId, it.deviceKeyId) ==
            listOf(existing.payload.restaurantId, existing.payload.terminalId, existing.payload.deviceKeyId)) { "Activation identity does not match existing license" } }
        require(spec.sequence == existing.payload.licenseSequence + 1) { "Renewal sequence must be exactly previous + 1" }
        val synthetic = ActivationRequestV1(restaurantId = existing.payload.restaurantId, terminalId = existing.payload.terminalId,
            deviceKeyId = existing.payload.deviceKeyId, generatedAtDeviceUtc = clock.instant().toString(),
            requestId = request?.requestId ?: "renewal:${existing.payload.licenseId}")
        return issue(synthetic, spec, key)
    }
}

@Serializable
data class AuditRecord(val licenseId: String, val licenseSequence: Long, val restaurantId: String,
    val terminalId: String, val deviceKeyId: String, val planCode: String, val issuedAtUtc: String,
    val expiresAtUtc: String, val graceUntilUtc: String, val activationRequestId: String,
    val outputSha256: String, val status: String = "ACTIVE")

class AuditStore(private val path: Path) {
    fun append(license: SignedLicenseV1, requestId: String, output: ByteArray) {
        val p = license.payload
        val hash = MessageDigest.getInstance("SHA-256").digest(output).joinToString("") { "%02x".format(it) }
        val record = AuditRecord(p.licenseId, p.licenseSequence, p.restaurantId, p.terminalId, p.deviceKeyId,
            p.planCode, p.issuedAtUtc, p.expiresAtUtc, p.graceUntilUtc, requestId, hash)
        path.parent?.let(Files::createDirectories)
        Files.writeString(path, auditJson.encodeToString(record) + "\n", StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
    fun records(): List<AuditRecord> = if (!Files.exists(path)) emptyList() else Files.readAllLines(path)
        .filter { it.isNotBlank() }.map { auditJson.decodeFromString<AuditRecord>(it) }
}
