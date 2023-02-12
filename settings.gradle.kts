pluginManagement {
    repositories {
        maven("https://gitlab.3m5.de/api/v4/groups/223/-/packages/maven") // 3m5. Gradle plugins
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

    plugins {
        kotlin("multiplatform").version(extra["kotlin.version"] as String)
        id("org.jetbrains.compose").version(extra["compose.version"] as String)
    }
}

rootProject.name = "stable-diffusion-webui-client"
include(
    // "sd-client-api", // turns out the standard openapi kotlin client doesnt even compile... WAT
    "sd-client-app",
)
// includeBuild("sd-client-generated")
