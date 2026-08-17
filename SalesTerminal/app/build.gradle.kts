import java.io.File
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kgp.serialization)
    alias(libs.plugins.kgp.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

fun org.gradle.api.provider.ProviderFactory.externalValue(property: String, environment: String) =
    gradleProperty(property).orElse(environmentVariable(environment))

val productionPilot = providers.externalValue("PRODUCTION_PILOT", "PRODUCTION_PILOT")
    .orElse("false").get().toBooleanStrictOrNull() ?: error("PRODUCTION_PILOT must be true or false")

val authorityKey = providers.externalValue("LICENSE_AUTHORITY_PUBLIC_KEY", "LICENSE_AUTHORITY_PUBLIC_KEY")
    .orElse("").get().trim()
val expectedCertificate = providers.externalValue("EXPECTED_RELEASE_CERT_SHA256", "EXPECTED_RELEASE_CERT_SHA256")
    .orElse("").get().replace(":", "").uppercase()

val pilotSigningMaterial = if (productionPilot) {
    fun required(property: String, environment: String): String =
        providers.externalValue(property, environment).orNull?.takeIf { it.isNotBlank() }
            ?: error("Production pilot release requires $property (or $environment)")

    require(authorityKey.isNotBlank()) { "Production pilot release requires LICENSE_AUTHORITY_PUBLIC_KEY" }
    val decodedAuthority = runCatching { Base64.getDecoder().decode(authorityKey) }
        .getOrElse { error("LICENSE_AUTHORITY_PUBLIC_KEY must be Base64 X.509 public-key data") }
    val publicKey = runCatching {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(decodedAuthority))
    }.getOrElse { error("LICENSE_AUTHORITY_PUBLIC_KEY must be an EC X.509 public key") }
    require(publicKey is ECPublicKey && publicKey.params.curve.field.fieldSize == 256) {
        "LICENSE_AUTHORITY_PUBLIC_KEY must be a P-256 EC public key"
    }
    require(expectedCertificate.matches(Regex("[0-9A-F]{64}")) && expectedCertificate.any { it != '0' }) {
        "EXPECTED_RELEASE_CERT_SHA256 must be a non-zero SHA-256 certificate fingerprint"
    }

    val keystorePath = File(required("ANDROID_SIGNING_KEYSTORE", "ANDROID_SIGNING_KEYSTORE"))
    require(keystorePath.isAbsolute) { "ANDROID_SIGNING_KEYSTORE must be an absolute external path" }
    require(keystorePath.exists() && keystorePath.isFile) { "Android signing keystore does not exist" }
    val repositoryRoot = rootProject.projectDir.canonicalFile.parentFile
    require(!keystorePath.canonicalFile.toPath().startsWith(repositoryRoot.toPath())) {
        "Android signing keystore must remain outside the product source directory"
    }
    val alias = required("ANDROID_SIGNING_ALIAS", "ANDROID_SIGNING_ALIAS")
    val storePassword = required("ANDROID_SIGNING_STORE_PASSWORD", "ANDROID_SIGNING_STORE_PASSWORD")
    val keyPassword = required("ANDROID_SIGNING_KEY_PASSWORD", "ANDROID_SIGNING_KEY_PASSWORD")
    require(!gradle.startParameter.isConfigurationCacheRequested) {
        "Production pilot release must use --no-configuration-cache so passwords are not cached"
    }
    val keyStore = sequenceOf("PKCS12", "JKS").mapNotNull { type ->
        runCatching {
            KeyStore.getInstance(type).apply {
                keystorePath.inputStream().use { load(it, storePassword.toCharArray()) }
            }
        }.getOrNull()
    }.firstOrNull() ?: error("Android signing keystore could not be opened")
    require(keyStore.isKeyEntry(alias)) { "ANDROID_SIGNING_ALIAS is not a private-key entry in the keystore" }
    requireNotNull(runCatching { keyStore.getKey(alias, keyPassword.toCharArray()) }.getOrNull()) {
        "Android signing key password could not unlock the configured alias"
    }
    val actualCertificate = MessageDigest.getInstance("SHA-256")
        .digest(requireNotNull(keyStore.getCertificate(alias)) { "Signing alias has no certificate" }.encoded)
        .joinToString("") { "%02X".format(it) }
    require(actualCertificate == expectedCertificate) {
        "EXPECTED_RELEASE_CERT_SHA256 does not match the configured Android signing certificate"
    }
    arrayOf(keystorePath.path, alias, storePassword, keyPassword)
} else null

android {
    namespace = "com.venkoi.terminal"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.venkoi.terminal"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0-pilot.1"

        testInstrumentationRunner = "com.venkoi.terminal.HiltTestRunner"
        buildConfigField("String", "LICENSE_AUTHORITY_PUBLIC_KEY", "\"\"")
        buildConfigField("String", "EXPECTED_RELEASE_CERT_SHA256", "\"\"")
    }

    buildTypes {
        debug {
            val enforce = providers.gradleProperty("ENFORCE_LICENSE_IN_DEBUG").orElse("false").get().toBoolean()
            val developmentKey = providers.gradleProperty("DEV_LICENSE_AUTHORITY_PUBLIC_KEY").orElse("").get()
            buildConfigField("boolean", "ENFORCE_LICENSE_IN_DEBUG", enforce.toString())
            buildConfigField("String", "LICENSE_AUTHORITY_PUBLIC_KEY", "\"${developmentKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        }
        release {
            buildConfigField("String", "LICENSE_AUTHORITY_PUBLIC_KEY", "\"${authorityKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
            buildConfigField("String", "EXPECTED_RELEASE_CERT_SHA256", "\"${expectedCertificate.replace("\"", "\\\"")}\"")
            pilotSigningMaterial?.let { material ->
                signingConfig = signingConfigs.create("productionPilot") {
                    storeFile = file(material[0])
                    keyAlias = material[1]
                    storePassword = material[2]
                    keyPassword = material[3]
                    enableV1Signing = true
                    enableV2Signing = true
                }
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "src/main/keepRules/rules.keep")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Built-in Kotlin configuration (AGP 9.0+)
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.venkoi:license-contract:1.0.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val verifyNoPrivateKeyMaterial by tasks.registering {
    group = "verification"
    description = "Rejects private authority key material in product-owned application content."
    val productContent = listOf("main", "debug", "release").map { layout.projectDirectory.dir("src/$it") }
    inputs.files(productContent)
    doLast {
        val forbiddenNames = listOf("authority-private.pem", "license-authority-private.pem")
        val privateKeyHeader = "-----BEGIN PRIVATE KEY-----"
        productContent.flatMap { directory ->
            if (directory.asFile.exists()) directory.asFile.walkTopDown().filter { it.isFile }.toList() else emptyList()
        }.forEach { file ->
            check(forbiddenNames.none { file.name.equals(it, ignoreCase = true) }) {
                "Private-key artifact is forbidden in Sales Terminal: ${file.path}"
            }
            if (file.extension.lowercase() in setOf("kt", "java", "xml", "json", "txt", "pem", "key", "properties")) {
                check(!file.readText().contains(privateKeyHeader)) {
                    "Private-key material is forbidden in Sales Terminal: ${file.path}"
                }
            }
        }
    }
}

tasks.named("preBuild").configure { dependsOn(verifyNoPrivateKeyMaterial) }
