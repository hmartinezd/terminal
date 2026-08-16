# License Contract

`com.venkoi:license-contract:1.0.0` is the public, non-secret schema-version-1 wire contract shared by Sales Terminal and License Admin. It is a pure Kotlin/JVM library and requires no Android SDK.

Run `./gradlew build` to build and test it independently. Run `./gradlew publishToMavenLocal` to make the artifact available to independent local consumer builds. Incompatible wire or canonical-encoding changes require a new contract and schema version.
