import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.lawkeys"
version = "0.8.1"
description = "HCFCore - Open source HCF/Kitmap core plugin for Paper"

java {
    // Paper 26.2 requires JDK 25 to compile/run. Verified: docs.papermc.io, PaperMC/Paper GitHub (28/08/2026).
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()

    // Official PaperMC repository (verified: github.com/PaperMC/Paper, 28/08/2026)
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    // VaultAPI is distributed via JitPack (verified: github.com/MilkBowl/VaultAPI, 28/08/2026)
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }

    // Official Lunar Client / Apollo repository (verified: lunarclient.dev/maven-repository, 28/08/2026)
    maven {
        name = "lunarclient"
        url = uri("https://repo.lunarclient.dev")
    }
}

dependencies {
    // --- Paper API (provided by the server at runtime) ---
    // Version target: latest stable Paper 26.2 build. Update this string as new Paper stable
    // releases ship — see CONTRIBUTING.md section 6 (always verify against docs.papermc.io before bumping).
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    // --- Persistence: HikariCP connection pool + drivers (MySQL prod / SQLite dev) ---
    // Shaded into the plugin jar since these are NOT provided by the server.
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.mysql:mysql-connector-j:9.1.0")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    // --- Soft-dependencies: economy / permissions / client integrations ---
    // All "compileOnly" — the plugin must run without these installed (see ARCHITECTURE.md section 11).
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        // VaultAPI transitively pulls an old org.bukkit:bukkit artifact that conflicts
        // with paper-api's own Bukkit capability. Paper API already provides everything
        // VaultAPI needs at compile time - exclude the transitive dependency entirely.
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("net.luckperms:api:5.5")
    compileOnly("com.lunarclient:apollo-api:1.2.7")

    // --- Tests ---
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // The YAML parser Paper 26.2 ships (libraries/org/yaml/snakeyaml/2.6): the tests read
    // the shipped files as the server does, YAML 1.1 and all.
    testImplementation("org.yaml:snakeyaml:2.6")
    // Gradle 9 no longer puts the JUnit Platform launcher on the test runtime
    // classpath by itself: without this, every test task fails before running a
    // single test with "Failed to load JUnit Platform". The version comes from the
    // BOM above, since testRuntimeClasspath extends testImplementation.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    // The code is meant to compile with "-Xlint:all" silent. It is checked here, on
    // every build and in CI, rather than left to whoever last ran javac by hand.
    //
    // Warnings are not errors on purpose. A Paper version bump can deprecate an API
    // the plugin uses - it has happened once already, with PlayerLoginEvent - and
    // that deserves a visible warning and a considered fix, not a build that refuses
    // to produce a jar. Add "-Werror" below if you would rather it were fatal.
    withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-Xlint:all")
    }

    test {
        useJUnitPlatform()
    }

    // Relocate shaded dependencies to avoid classpath collisions with other plugins
    // that might also bundle HikariCP/JDBC drivers.
    shadowJar {
        archiveClassifier.set("")
        relocate("com.zaxxer.hikari", "com.lawkeys.hcfcore.libs.hikari")
        // org.sqlite is NOT relocated: sqlite-jdbc is native code, and JNI binds a
        // native method by the fully qualified name of its class (JNI spec,
        // "Resolving Native Method Names"). Relocated, NativeDB cannot find its own
        // functions - UnsatisfiedLinkError at the first connection, found on the
        // first start on a real Paper 26.2 server. Paper ships sqlite-jdbc and
        // mysql-connector-j itself (libraries/ of the server); the plugin classloader
        // asks the server first, so the bundled copy here is only a fallback.
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
