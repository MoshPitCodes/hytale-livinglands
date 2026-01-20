import java.util.Properties

plugins {
    java
}

// Load version from single source of truth
val versionProps = Properties().apply {
    file("version.properties").reader().use { load(it) }
}

group = "com.livinglands"
version = versionProps.getProperty("mod.version") ?: "unknown"

repositories {
    mavenCentral()
    maven("https://repo.hypixel.net/repository/Hytale/")
}

// Path to Hytale Server installation (Windows path accessed via WSL)
val hytaleServerPath = "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/install/release/package/game/latest/Server"

dependencies {
    // Hytale Server API - compile against installed server, provided at runtime
    // Note: Builds must be done locally as the server JAR is not publicly available
    compileOnly(files("$hytaleServerPath/HytaleServer.jar"))

    // Annotations
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    // Copy version.properties to resources so it's bundled in the JAR
    processResources {
        from("version.properties")
    }

    jar {
        archiveFileName.set("${project.name}-${project.version}.jar")

        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version
            )
        }
    }
}
