plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kgp.serialization)
}

group = "com.venkoi"
version = "1.0.0"

kotlin { jvmToolchain(17) }
application { mainClass.set("com.venkoi.licenseadmin.cli.MainKt") }

dependencies {
    implementation(project(":LicenseContract"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.test { useJUnit() }
