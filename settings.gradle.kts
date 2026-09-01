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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val localProperties = Properties().apply {
    val file = rootDir.resolve("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

val dualCoreEnginePath =
    localProperties.getProperty("dualCoreEnginePath")
        ?: providers.gradleProperty("dualCoreEnginePath").orNull
        ?: System.getenv("DUALCORE_ENGINE_PATH")

val privateProperties = Properties().apply {
    val file = rootDir.resolve("gradle-private.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

val dualCoreRepoUrl =
    privateProperties.getProperty("dualCoreRepoUrl")
        ?: providers.gradleProperty("dualCoreRepoUrl").orNull
        ?: System.getenv("DUALCORE_REPO_URL")
val dualCoreRepoUser =
    privateProperties.getProperty("dualCoreRepoUser")
        ?: providers.gradleProperty("dualCoreRepoUser").orNull
        ?: System.getenv("DUALCORE_REPO_USER")
val dualCoreRepoToken =
    privateProperties.getProperty("dualCoreRepoToken")
        ?: providers.gradleProperty("dualCoreRepoToken").orNull
        ?: System.getenv("DUALCORE_REPO_TOKEN")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://www.jitpack.io") }

        if (!dualCoreRepoUrl.isNullOrBlank()) {
            maven {
                name = "dualCorePrivate"
                url = uri(dualCoreRepoUrl)
                credentials {
                    username = dualCoreRepoUser.orEmpty()
                    password = dualCoreRepoToken.orEmpty()
                }
            }
        }
    }
}

rootProject.name = "DualSpaceApp"
include(":app")

// Local development: keep the engine in a separate private directory/repository.
// Nothing beneath dualCoreEnginePath is copied into this public app repository.
if (!dualCoreEnginePath.isNullOrBlank()) {
    val engineRoot = file(dualCoreEnginePath).canonicalFile
    val engineModule = engineRoot.resolve("DualCore")
    val reflectionModule = engineRoot.resolve("DualCoreReflection")
    val compilerModule = engineRoot.resolve("DualCoreCompiler")

    require(engineModule.isDirectory && reflectionModule.isDirectory && compilerModule.isDirectory) {
        "dualCoreEnginePath must point to the root of DualCore-Engine-Private. " +
            "Missing DualCore, DualCoreReflection, or DualCoreCompiler under: $engineRoot"
    }

    include(":DualCore", ":DualCoreReflection", ":DualCoreCompiler")
    project(":DualCore").projectDir = engineModule
    project(":DualCoreReflection").projectDir = reflectionModule
    project(":DualCoreCompiler").projectDir = compilerModule
}
