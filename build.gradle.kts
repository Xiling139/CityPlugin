plugins {
    id("java")
}

group = "com.xiling"
version = "2.0.0"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        name = "EngineHub"
        url = uri("https://maven.enginehub.org/repo/")
    }
}

dependencies {
    // Paper API for Minecraft 1.21.11
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")

    // Source: https://mvnrepository.com/artifact/com.sk89q.worldedit/worldedit-bukkit
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.0")
    compileOnly("com.sk89q.worldedit:worldedit-core:7.4.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.compileJava {
    options.encoding = "UTF-8"
    // Target Java 22 bytecode so the plugin runs on the Minecraft 1.21.11 server JVM
    options.release = 22
}

tasks.jar {
    from(sourceSets.main.get().output)
    archiveFileName.set("CityPlugin-${version}.jar")

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
