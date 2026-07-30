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

plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.shadow)
}

group = properties["plugin_group"]!!
version = properties["plugin_version"]!!

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))

    if (System.getProperty("sources")?.toBoolean() == true) {
        withSourcesJar()
    }
}

dependencies {
    // Spigot
    compileOnly(libs.spigot.api) {
        exclude("com.google.code.gson", "gson") // Implemented manually
    }
    // Paper Lib, performance improvements on Paper-based servers and async teleporting on Folia
    implementation(libs.paperlib)
    // Scheduler abstraction across Bukkit/Paper/Folia
    implementation(libs.folialib)

    // Database source implementation
    implementation(libs.hikaricp) {
        exclude("org.slf4j", "slf4j-api")
    }
    // PostgreSQL JDBC driver (MySQL/SQLite drivers are provided by the server at runtime)
    implementation(libs.postgresql)
    // Implemented manually mainly due to older server versions implementing versions of GSON
    // which don't support records.
    implementation(libs.gson)
    // Nice annotations, I prefer these to Lombok's, https://www.jetbrains.com/help/idea/annotating-source-code.html
    compileOnly(libs.annotations)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Okaeri configuration
    implementation(libs.okaeri.configs.yaml) {
        exclude("org.yaml", "snakeyaml")
    }
    compileOnly(libs.snakeyaml)

    // Plugin compatibility
    compileOnly(libs.placeholderapi)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"

    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)

    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.jar {
    enabled = false // Shadow produces our jar files
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    archiveFileName.set("${rootProject.name}-$version.jar")
    mergeServiceFiles()

    fun relocate(pkg: String) = relocate(pkg, "${project.group}.depend.$pkg")
    relocate("com.google.gson")
    relocate("com.google.errorprone")
    relocate("eu.okaeri")
    relocate("io.papermc.lib")
    relocate("com.zaxxer.hikari")
    relocate("com.tcoded.folialib")
    relocate("org.postgresql")
}

publishing {
    val repoUrl = System.getenv("REPO_URL") ?: "https://repo.jsinco.dev/releases"
    val user = System.getenv("REPO_USERNAME")
    val pass = System.getenv("REPO_SECRET")

    if (user == null || pass == null) {
        return@publishing
    }

    repositories {
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
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            artifact(tasks.shadowJar)
        }
    }
}
