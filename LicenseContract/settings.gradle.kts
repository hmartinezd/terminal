pluginManagement {
    repositories { mavenCentral(); gradlePluginPortal() }
    plugins {
        kotlin("jvm") version "2.3.20"
        kotlin("plugin.serialization") version "2.3.20"
    }
}
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "license-contract"
