package com.venkoi.licenseadmin.cli

import com.venkoi.licenseadmin.audit.AuditRecord
import com.venkoi.licenseadmin.audit.AuditStore
import com.venkoi.licenseadmin.contract.ContractValidation
import com.venkoi.licenseadmin.crypto.AuthorityKeys
import com.venkoi.licenseadmin.issuer.IssueSpec
import com.venkoi.licenseadmin.issuer.LicenseIssuerService
import com.venkoi.terminal.licensing.ActivationRequestV1
import com.venkoi.terminal.licensing.SignedLicenseV1
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.time.Instant
import kotlin.io.path.exists

private val wireJson = Json { prettyPrint = true; ignoreUnknownKeys = false }

private class Args(values: Array<String>) {
    val command = values.firstOrNull() ?: "help"
    private val rest = values.drop(1)
    fun value(name: String): String? = rest.indexOf(name).takeIf { it >= 0 }?.let { rest.getOrNull(it + 1) }
    fun required(name: String): String = value(name) ?: error("Missing $name")
    fun flag(name: String): Boolean = name in rest
    fun positional(): String? = rest.firstOrNull { !it.startsWith("--") }
}

fun main(raw: Array<String>) = try {
    val args = Args(raw)
    when (args.command) {
        "generate-keypair" -> generate(args)
        "key-info" -> keyInfo(args)
        "inspect-request" -> printRequest(readRequest(Path.of(args.positional() ?: error("Missing activation file"))))
        "issue" -> issue(args, renewal = false)
        "renew" -> issue(args, renewal = true)
        "list" -> query(args) { args.value("--restaurant") == null || it.restaurantId == args.value("--restaurant") }
        "show-license" -> query(args) { it.licenseId == (args.positional() ?: args.required("--license-id")) }
        "show-device" -> query(args) { it.deviceKeyId == (args.positional() ?: args.required("--device-key-id")) }
        else -> usage()
    }
} catch (e: Exception) {
    System.err.println("Error: ${e.message}")
    kotlin.system.exitProcess(2)
}

private fun usage() = println("""license-admin commands:
  generate-keypair --private <pkcs8.pem> --public <x509.pem> [--force]
  key-info --public-key <x509.pem>
  inspect-request <activation.json>
  issue --request <json> --private-key <pem> --public-key <pem> --plan <code> --sequence <n> --expires <instant> --grace-until <instant> --output <json> [--audit <jsonl>] [--yes]
  renew --license <json> [--request <json>] --private-key <pem> --public-key <pem> [--plan <code>] --expires <instant> --grace-until <instant> --output <json> [--audit <jsonl>] [--yes]
  list --audit <jsonl> [--restaurant <id>]
  show-license <id> --audit <jsonl>
  show-device <deviceKeyId> --audit <jsonl>""")

private fun readRequest(path: Path) = ContractValidation.request(
    wireJson.decodeFromString<ActivationRequestV1>(Files.readString(path)))

private fun printRequest(request: ActivationRequestV1) = println(
    "Restaurant: ${request.restaurantId}\nTerminal: ${request.terminalId}\n" +
        "Device: ${request.deviceKeyId}\nGenerated: ${request.generatedAtDeviceUtc}\nRequest: ${request.requestId}")

private fun issue(args: Args, renewal: Boolean) {
    val keys = AuthorityKeys.loadAndValidate(Path.of(args.required("--private-key")), Path.of(args.required("--public-key")))
    val request = args.value("--request")?.let { readRequest(Path.of(it)) }
    val existing = if (renewal) wireJson.decodeFromString<SignedLicenseV1>(
        Files.readString(Path.of(args.required("--license")))) else null
    require(renewal || request != null) { "Issue requires --request" }
    val sequence = existing?.payload?.licenseSequence?.plus(1) ?: args.required("--sequence").toLong()
    val plan = args.value("--plan") ?: existing?.payload?.planCode ?: error("Missing --plan")
    val spec = IssueSpec(plan, sequence, Instant.parse(args.required("--expires")),
        Instant.parse(args.required("--grace-until")), args.flag("--allow-expired-for-testing"))
    val identity = request ?: ActivationRequestV1(restaurantId = existing!!.payload.restaurantId,
        terminalId = existing.payload.terminalId, deviceKeyId = existing.payload.deviceKeyId,
        generatedAtDeviceUtc = Instant.now().toString(), requestId = "renewal:${existing.payload.licenseId}")
    println("Restaurant: ${identity.restaurantId}\nTerminal: ${identity.terminalId}\nDevice: ${identity.deviceKeyId}\n" +
        "Plan: $plan\nSequence: $sequence\nExpires: ${spec.expires}\nGrace until: ${spec.grace}")
    if (!args.flag("--yes")) {
        print("Type YES to sign: ")
        require(readlnOrNull() == "YES") { "Issuance cancelled" }
    }
    val license = if (renewal) LicenseIssuerService().renew(existing!!, request, spec, keys)
        else LicenseIssuerService().issue(request!!, spec, keys)
    val outputPath = Path.of(args.required("--output"))
    require(!outputPath.exists() || args.flag("--force")) { "Output exists; use --force" }
    outputPath.parent?.let(Files::createDirectories)
    val output = (wireJson.encodeToString(license) + "\n").toByteArray()
    Files.write(outputPath, output)
    AuditStore(Path.of(args.value("--audit") ?: "license-audit.jsonl")).append(license, identity.requestId, output)
    println("Issued: ${license.payload.issuedAtUtc}\nOutput: $outputPath\nAuthority: ${AuthorityKeys.fingerprint(keys.publicKey)}")
}

private fun generate(args: Args) {
    val privatePath = Path.of(args.required("--private"))
    val publicPath = Path.of(args.required("--public"))
    require(args.flag("--force") || (!privatePath.exists() && !publicPath.exists())) { "Key file exists; use --force" }
    privatePath.parent?.let(Files::createDirectories)
    publicPath.parent?.let(Files::createDirectories)
    val pair = AuthorityKeys.generate()
    Files.writeString(privatePath, AuthorityKeys.pem("PRIVATE KEY", pair.private.encoded))
    runCatching { Files.setPosixFilePermissions(privatePath,
        setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)) }
    Files.writeString(publicPath, AuthorityKeys.pem("PUBLIC KEY", pair.public.encoded))
    println("Public key: $publicPath\nFingerprint: ${AuthorityKeys.fingerprint(pair.public)}\n" +
        "LICENSE_AUTHORITY_PUBLIC_KEY=${AuthorityKeys.base64(pair.public)}")
    System.err.println("WARNING: loss prevents future compatible renewals; compromise permits forged licenses. " +
        "Keep an offline backup and store the private key outside this project.")
}

private fun keyInfo(args: Args) {
    val key = AuthorityKeys.publicKey(Path.of(args.required("--public-key")))
    println("SHA-256 fingerprint: ${AuthorityKeys.fingerprint(key)}\n" +
        "LICENSE_AUTHORITY_PUBLIC_KEY=${AuthorityKeys.base64(key)}\n${AuthorityKeys.pem("PUBLIC KEY", key.encoded)}")
}

private fun query(args: Args, predicate: (AuditRecord) -> Boolean) {
    val records = AuditStore(Path.of(args.value("--audit") ?: "license-audit.jsonl")).records().filter(predicate)
    records.forEach { println("${it.licenseId} seq=${it.licenseSequence} restaurant=${it.restaurantId} " +
        "terminal=${it.terminalId} device=${it.deviceKeyId} plan=${it.planCode} expires=${it.expiresAtUtc} ${it.status}") }
    val activeDevices = records.filter { Instant.parse(it.graceUntilUtc) > Instant.now() && it.status == "ACTIVE" }
        .map { it.deviceKeyId }.distinct().size
    println("Records: ${records.size}; distinct active devices: $activeDevices")
}
