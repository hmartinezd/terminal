plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kgp.serialization)
}

kotlin { jvmToolchain(17) }
application { mainClass.set("com.venkoi.terminal.issuer.MainKt") }
dependencies {
    implementation(project(":license-contract"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
