plugins {
    // Downloads the Java toolchain when the machine does not have it. The version catalog does not reach this block.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // Every repository is declared here; a build or a plugin that adds its own fails.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    // Maven Central serves everything but the groups below, and each of those comes only from its own repository: no
    // other repository can serve a paper-api, and a slow one cannot break other lookups.
    repositories {
        mavenCentral()
        // Paper's API and the two libraries it needs that Maven Central lacks (Brigadier, bungeecord-chat).
        exclusiveContent {
            forRepository { maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" } }
            filter {
                includeGroupAndSubgroups("io.papermc")
                includeGroup("com.mojang")
                includeGroup("net.md-5")
            }
        }
        exclusiveContent {
            forRepository { maven("https://maven.enginehub.org/repo/") { name = "enginehub" } }
            filter {
                includeGroupAndSubgroups("com.sk89q")
                includeGroupAndSubgroups("org.enginehub")
            }
        }
        exclusiveContent {
            forRepository { maven("https://repo.momirealms.net/releases/") { name = "momirealms" } }
            filter { includeGroup("net.momirealms") }
        }
    }
}

rootProject.name = "FrameSort"
