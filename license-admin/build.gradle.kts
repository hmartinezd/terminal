plugins {
    application
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
}

group = "com.venkoi"
version = "1.0.0"

repositories { mavenCentral() }
kotlin { jvmToolchain(17) }
application { mainClass.set("com.venkoi.licenseadmin.cli.MainKt") }

dependencies {
    implementation("com.venkoi:license-contract:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.test { useJUnit() }
