plugins {
    // add toolchain resolver
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" } // Spigot API, PaperLib
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") { name = "extendedclip" } // PlaceholderAPI
        maven("https://storehouse.okaeri.eu/repository/maven-public/") { name = "okaeri" } // Okaeri Config
        maven("https://repo.tcoded.com/releases") { name = "tcoded" } // FoliaLib
    }
}

rootProject.name = "BreweryX"
