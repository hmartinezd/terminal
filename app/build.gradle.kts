plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kgp.serialization)
    alias(libs.plugins.kgp.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.venkoi.terminal"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.venkoi.terminal"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.venkoi.terminal.HiltTestRunner"
        buildConfigField("String", "LICENSE_AUTHORITY_PUBLIC_KEY", "\"\"")
        buildConfigField("String", "EXPECTED_RELEASE_CERT_SHA256", "\"\"")
    }

    buildTypes {
        debug {
            // Debug authorization is supplied only by src/debug; it cannot be toggled at runtime.
        }
        release {
            val authorityKey = providers.gradleProperty("LICENSE_AUTHORITY_PUBLIC_KEY").orElse("").get()
            val certificate = providers.gradleProperty("EXPECTED_RELEASE_CERT_SHA256").orElse("").get()
            buildConfigField("String", "LICENSE_AUTHORITY_PUBLIC_KEY", "\"${authorityKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
            buildConfigField("String", "EXPECTED_RELEASE_CERT_SHA256", "\"${certificate.replace("\"", "\\\"")}\"")
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
