package com.venkoi.terminal.issuer

import com.venkoi.terminal.licensing.*
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import kotlin.io.path.exists

private class Args(values: Array<String>) {
    val command = values.firstOrNull() ?: "help"
    private val rest = values.drop(1)
    fun value(name: String): String? = rest.indexOf(name).takeIf { it >= 0 }?.let { rest.getOrNull(it + 1) }
    fun required(name: String) = value(name) ?: error("Missing $name")
    fun flag(name: String) = name in rest
    fun positional() = rest.firstOrNull { !it.startsWith("--") }
}

fun main(raw: Array<String>) = try {
    val args = Args(raw)
    when (args.command) {
        "generate-keypair" -> generate(args)
        "key-info" -> keyInfo(args)
        "inspect-request" -> printRequest(readRequest(Path.of(args.positional() ?: error("Missing activation file"))))
        "issue" -> issue(args, false)
        "renew" -> issue(args, true)
        "list" -> query(args) { args.value("--restaurant") == null || it.restaurantId == args.value("--restaurant") }
        "show-license" -> query(args) { it.licenseId == (args.positional() ?: args.required("--license-id")) }
        "show-device" -> query(args) { it.deviceKeyId == (args.positional() ?: args.required("--device-key-id")) }
        else -> usage()
    }
} catch (e: Exception) { System.err.println("Error: ${e.message}"); kotlin.system.exitProcess(2) }

private fun usage() = println("""license-issuer commands:
  generate-keypair --private <file> --public <file> [--force]
  key-info (--private-key <pkcs8.pem> | --public-key <x509.pem>)
  inspect-request <activation.json>
  issue --request <json> --private-key <pem> --plan <code> --sequence <n> --expires <instant> --grace-until <instant> --output <json> [--audit <jsonl>] [--yes]
  renew --license <json> [--request <json>] --private-key <pem> --plan <code> --expires <instant> --grace-until <instant> --output <json> [--audit <jsonl>] [--yes]
  list --audit <jsonl> [--restaurant <id>]
  show-license <id> --audit <jsonl>
  show-device <deviceKeyId> --audit <jsonl>""")

private fun readRequest(path: Path) = ContractValidation.request(JsonWire.json.decodeFromString<ActivationRequestV1>(Files.readString(path)))
private fun printRequest(r: ActivationRequestV1) = println("Restaurant: ${r.restaurantId}\nTerminal: ${r.terminalId}\nDevice: ${r.deviceKeyId}\nGenerated: ${r.generatedAtDeviceUtc}\nRequest: ${r.requestId}")

private fun issue(args: Args, renewal: Boolean) {
    val key = AuthorityKeys.privateKey(Path.of(args.required("--private-key")))
    val request = args.value("--request")?.let { readRequest(Path.of(it)) }
    val existing = if (renewal) JsonWire.json.decodeFromString<SignedLicenseV1>(Files.readString(Path.of(args.required("--license")))) else null
    require(renewal || request != null) { "Issue requires --request" }
    val sequence = if (renewal) existing!!.payload.licenseSequence + 1 else args.required("--sequence").toLong()
    val plan = args.value("--plan") ?: existing?.payload?.planCode ?: error("Missing --plan")
    val spec = IssueSpec(plan, sequence, Instant.parse(args.required("--expires")),
        Instant.parse(args.required("--grace-until")), args.flag("--allow-expired-for-testing"))
    val identity = request ?: ActivationRequestV1(restaurantId = existing!!.payload.restaurantId,
        terminalId = existing.payload.terminalId, deviceKeyId = existing.payload.deviceKeyId,
        generatedAtDeviceUtc = Instant.now().toString(), requestId = "renewal:${existing.payload.licenseId}")
    println("Restaurant: ${identity.restaurantId}\nTerminal: ${identity.terminalId}\nDevice: ${identity.deviceKeyId}\nPlan: $plan\nSequence: $sequence\nExpires: ${spec.expires}\nGrace until: ${spec.grace}")
    if (!args.flag("--yes")) { print("Type YES to sign: "); require(readlnOrNull() == "YES") { "Issuance cancelled" } }
    val license = if (renewal) LicenseIssuerService().renew(existing!!, request, spec, key)
        else LicenseIssuerService().issue(request!!, spec, key)
    val outputPath = Path.of(args.required("--output")); require(!outputPath.exists() || args.flag("--force")) { "Output exists; use --force" }
    val output = (JsonWire.json.encodeToString(license) + "\n").toByteArray()
    Files.write(outputPath, output)
    AuditStore(Path.of(args.value("--audit") ?: "license-audit.jsonl")).append(license, identity.requestId, output)
    println("Issued: ${license.payload.issuedAtUtc}\nOutput: $outputPath\nAuthority: ${AuthorityKeys.fingerprint(AuthorityKeys.derivedPublic(key))}")
}

private fun generate(args: Args) {
    val privatePath = Path.of(args.required("--private")); val publicPath = Path.of(args.required("--public"))
    require(args.flag("--force") || (!privatePath.exists() && !publicPath.exists())) { "Key file exists; use --force" }
    val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
    val pair = generator.generateKeyPair()
    Files.writeString(privatePath, AuthorityKeys.pem("PRIVATE KEY", pair.private.encoded))
    runCatching { Files.setPosixFilePermissions(privatePath, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)) }
    Files.writeString(publicPath, AuthorityKeys.pem("PUBLIC KEY", pair.public.encoded))
    println("Public key: $publicPath\nFingerprint: ${AuthorityKeys.fingerprint(pair.public)}\nAndroid LICENSE_AUTHORITY_PUBLIC_KEY=${AuthorityKeys.base64(pair.public)}")
    System.err.println("WARNING: loss prevents future compatible renewals; compromise permits forged licenses. Store the private key securely and outside this project.")
}

private fun keyInfo(args: Args) {
    val key = args.value("--public-key")?.let { AuthorityKeys.publicKey(Path.of(it)) }
        ?: AuthorityKeys.derivedPublic(AuthorityKeys.privateKey(Path.of(args.required("--private-key"))))
    println("SHA-256 fingerprint: ${AuthorityKeys.fingerprint(key)}\nLICENSE_AUTHORITY_PUBLIC_KEY=${AuthorityKeys.base64(key)}\n${AuthorityKeys.pem("PUBLIC KEY", key.encoded)}")
}

private fun query(args: Args, predicate: (AuditRecord) -> Boolean) {
    val records = AuditStore(Path.of(args.value("--audit") ?: "license-audit.jsonl")).records().filter(predicate)
    records.forEach { println("${it.licenseId} seq=${it.licenseSequence} restaurant=${it.restaurantId} terminal=${it.terminalId} device=${it.deviceKeyId} plan=${it.planCode} expires=${it.expiresAtUtc} ${it.status}") }
    val activeDevices = records.filter { Instant.parse(it.graceUntilUtc) > Instant.now() && it.status == "ACTIVE" }.map { it.deviceKeyId }.distinct().size
    println("Records: ${records.size}; distinct active devices: $activeDevices")
}
