import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    application
    kotlin("multiplatform") version "1.3.70"
}

group = "fr.acinq.eklair"
version = "1.0-SNAPSHOT"

application {
    mainClassName = "fr.acinq.eklair.Boot"
}

repositories {
    mavenLocal()
    google()
    jcenter()
    mavenCentral()
}

kotlin {
    /* Targets configuration omitted.
    *  To find out how to configure the targets, please follow the link:
    *  https://kotlinlang.org/docs/reference/building-mpp-with-gradle.html#setting-up-targets */

    jvm() {

    }
    linuxX64("linux") {
         binaries {
             executable()
         }
    }

    iosX64("ios"){
        binaries{
            framework()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("io.ktor:ktor-client-core:1.3.1")
                implementation("io.ktor:ktor-network:1.3.1")
                implementation(project(":secp256k1-lib"))
                implementation(project(":eklair-lib"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation("fr.acinq.bitcoin:secp256k1-jni:1.3")
                implementation("io.ktor:ktor-client-okhttp:1.3.1")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("org.bouncycastle:bcprov-jdk15on:1.64")
            }
        }
        val iosMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.4")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:1.3.4")
                implementation("io.ktor:ktor-network:1.3.1")
            }
        }

    }
}


// This task attaches native framework built from ios module to Xcode project
// (see iosApp directory). Don't run this task directly,
// Xcode runs this task itself during its build process.
// Before opening the project from iosApp directory in Xcode,
// make sure all Gradle infrastructure exists (gradle.wrapper, gradlew).
task("copyFramework") {
    val buildType: String = project.findProperty("kotlin.build.type")?.toString() ?: "DEBUG"
    val target: String = project.findProperty("kotlin.target")?.toString() ?: "ios"
    val kotlinNativeTarget = kotlin.targets.findByName(target) as KotlinNativeTarget
    val linkTask: Task = kotlinNativeTarget.binaries.getFramework(buildType).linkTask
    dependsOn(linkTask)

    doLast {
        val srcFile: File = kotlinNativeTarget.binaries.getFramework(buildType).outputFile
        val targetDir = System.getProperty("configuration.build.dir") ?: project.buildDir.path
        println("\uD83C\uDF4E Copying ${srcFile} to ${targetDir}")
        copy {
            from(srcFile.parent)
            into(targetDir)
            include("*.framework/**")
            include("*.framework.dSYM/**")
        }
    }
}
