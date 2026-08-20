plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kgp.serialization)
    `maven-publish`
}

group = "com.venkoi"
version = "1.0.0"
kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
tasks.test { useJUnit() }
publishing { publications { create<MavenPublication>("contract") { from(components["java"]) } } }
