import java.util.Properties

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// GitHub Packages credentials for karoo-ext: `gpr.user` / `gpr.key` in local.properties
// (never committed), falling back to the USERNAME / TOKEN environment variables in CI.
val localProperties = Properties().apply {
    val file = settingsDir.resolve("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun gprCredential(property: String, env: String): String? =
    localProperties.getProperty(property)
        ?: providers.gradleProperty(property).orNull
        ?: System.getenv(env)

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
            credentials {
                username = gprCredential("gpr.user", "USERNAME")
                password = gprCredential("gpr.key", "TOKEN")
            }
            content { includeGroup("io.hammerhead") }
        }
    }
}

rootProject.name = "hammerhead-karoo-extensions"

// Gradle path :device-juice, sources under device-juice/app (PLAN.md 6.1).
include(":device-juice")
project(":device-juice").projectDir = file("device-juice/app")
