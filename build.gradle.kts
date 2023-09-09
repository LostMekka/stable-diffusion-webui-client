import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

group = "de.lostmekka"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm {
        jvmToolchain(11)
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("com.github.kittinunf.fuel:fuel:2.3.1")
                implementation("com.github.kittinunf.fuel:fuel-jackson:2.3.1")
                implementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
                implementation("io.ktor:ktor-client-websockets:2.3.1")
                implementation("io.ktor:ktor-client-cio-jvm:2.3.1")
            }
        }
        val jvmTest by getting
    }
}

compose.desktop {
    application {
        mainClass = "de.lostmekka.sdwuic.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "stable-diffusion-webui-client"
            packageVersion = "1.0.0"
        }
    }
}
