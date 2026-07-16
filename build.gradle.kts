/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2024 The Brewery Team
 *
 * This file is part of BreweryX.
 *
 * BreweryX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BreweryX is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BreweryX. If not, see <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

import org.apache.tools.ant.filters.ReplaceTokens
import java.nio.charset.Charset

plugins {
    id("java-library")
    id("maven-publish")
    id("com.gradleup.shadow") version "9.4.1"
}

group = "com.dre.brewery"
version = "3.7.0"

val langVersion: Int = 21
val encoding: String = "UTF-8"

repositories {
    mavenCentral()
    maven("https://maven.enginehub.org/repo/") // WorldEdit, WorldGuard
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") // PlaceholderAPI
    maven("https://storehouse.okaeri.eu/repository/maven-public/") // Okaeri Config
    maven("https://repo.papermc.io/repository/maven-public/") // PaperLib
    maven("https://repo.tcoded.com/releases") // FoliaLib
}

dependencies {
    // Spigot
    compileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT") {
        exclude("com.google.code.gson", "gson") // Implemented manually
    }
    // Paper Lib, performance improvements on Paper-based servers and async teleporting on Folia
    implementation("io.papermc:paperlib:1.0.8")
    // Scheduler abstraction across Bukkit/Paper/Folia
    implementation("com.tcoded:FoliaLib:0.5.2")

    // Database source implementation
    implementation("com.zaxxer:HikariCP:7.0.2") {
        exclude("org.slf4j", "slf4j-api")
    }
    // PostgreSQL JDBC driver (MySQL/SQLite drivers are provided by the server at runtime)
    implementation("org.postgresql:postgresql:42.7.13")
    // Implemented manually mainly due to older server versions implementing versions of GSON
    // which don't support records.
    implementation("com.google.code.gson:gson:2.11.0")
    // Nice annotations, I prefer these to Lombok's, https://www.jetbrains.com/help/idea/annotating-source-code.html
    compileOnly("org.jetbrains:annotations:26.0.1")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // Okaeri configuration
    implementation("eu.okaeri:okaeri-configs-yaml-snakeyaml:5.0.5") {
        exclude("org.yaml", "snakeyaml")
    }
    constraints {
        implementation("org.yaml:snakeyaml") {
            version {
                require("2.3")
                reject("1.33")
            }
        }
    }

    // Plugin Compatability
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.0-SNAPSHOT") // https://dev.bukkit.org/projects/worldedit/files
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.0-SNAPSHOT") // https://dev.bukkit.org/projects/worldguard/files
    compileOnly("me.clip:placeholderapi:2.11.5") // https://www.spigotmc.org/resources/placeholderapi.6245/history
}

tasks {

    build {
        dependsOn(shadowJar)
    }
    jar {
        enabled = false // Shadow produces our jar files
    }
    withType<JavaCompile>().configureEach {
        options.encoding = encoding
    }

    processResources {
        outputs.upToDateWhen { false }
        filter<ReplaceTokens>(
            mapOf(
                "tokens" to mapOf("version" to "${project.version}"),
                "beginToken" to "\${",
                "endToken" to "}"
            )
        ).filteringCharset = encoding
    }

    shadowJar {
        val pack = "com.dre.brewery.depend"
        fun relocate(pkg: String) = relocate(pkg, "$pack.$pkg")

        relocate("com.google.gson")
        relocate("com.google.errorprone")
        relocate("eu.okaeri")
        relocate("org.bson")
        relocate("io.papermc.lib")
        relocate("com.zaxxer.hikari")
        relocate("com.tcoded.folialib")
        relocate("org.postgresql")

        archiveClassifier.set("")
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(langVersion)
    val b = System.getProperty("sources")
    if (b != null && b.toBoolean()) {
        withSourcesJar()
    }
}


publishing {
    val repoUrl = System.getenv("REPO_URL") ?: "https://repo.jsinco.dev/releases"
    val user = System.getenv("REPO_USERNAME")
    val pass = System.getenv("REPO_SECRET")

    repositories {
        if (user == null || pass == null) {
            return@repositories
        }
        maven {
            url = uri(repoUrl)
            credentials(PasswordCredentials::class) {
                username = user
                password = pass
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }

    publications {
        if (user == null || pass == null) {
            return@publications
        }
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            artifact(tasks.shadowJar.get().archiveFile) {
                builtBy(tasks.shadowJar)
            }
        }
    }
}
