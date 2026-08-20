# License Contract

`com.venkoi:license-contract:1.0.0` is the public, non-secret schema-version-1 wire contract shared by Sales Terminal and License Admin. It is a pure Kotlin/JVM library and requires no Android SDK.

From the top-level workspace, run `./gradlew :LicenseContract:build` to build and test it. Run `./gradlew :LicenseContract:publishToMavenLocal` only when an independently published artifact is needed; workspace consumers use the project directly. Incompatible wire or canonical-encoding changes require a new contract and schema version.
