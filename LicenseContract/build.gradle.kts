plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
}

group = "com.venkoi"
version = "1.0.0"
kotlin { jvmToolchain(17) }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation("junit:junit:4.13.2")
}
tasks.test { useJUnit() }
publishing { publications { create<MavenPublication>("contract") { from(components["java"]) } } }
