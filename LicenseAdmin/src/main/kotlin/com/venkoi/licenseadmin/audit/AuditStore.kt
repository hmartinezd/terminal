package com.venkoi.licenseadmin.audit

import com.venkoi.terminal.licensing.SignedLicenseV1
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

@Serializable
data class AuditRecord(
    val licenseId: String,
    val licenseSequence: Long,
    val restaurantId: String,
    val terminalId: String,
    val deviceKeyId: String,
    val planCode: String,
    val issuedAtUtc: String,
    val expiresAtUtc: String,
    val graceUntilUtc: String,
    val activationRequestId: String,
    val outputSha256: String,
    val status: String = "ACTIVE"
)

class AuditStore(private val path: Path) {
    private val json = Json { ignoreUnknownKeys = false }

    fun append(license: SignedLicenseV1, requestId: String, output: ByteArray) {
        val payload = license.payload
        val hash = MessageDigest.getInstance("SHA-256").digest(output).joinToString("") { "%02x".format(it) }
        val record = AuditRecord(payload.licenseId, payload.licenseSequence, payload.restaurantId,
            payload.terminalId, payload.deviceKeyId, payload.planCode, payload.issuedAtUtc,
            payload.expiresAtUtc, payload.graceUntilUtc, requestId, hash)
        path.parent?.let(Files::createDirectories)
        Files.writeString(path, json.encodeToString(record) + "\n", StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    fun records(): List<AuditRecord> = if (!Files.exists(path)) emptyList() else Files.readAllLines(path)
        .filter(String::isNotBlank).map { json.decodeFromString<AuditRecord>(it) }
}
