plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kgp.serialization)
}

kotlin { jvmToolchain(17) }

dependencies { implementation(libs.kotlinx.serialization.json) }
