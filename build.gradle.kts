import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    kotlin("multiplatform") version "1.4.0"
    kotlin("plugin.serialization") version "1.4.0"
    `maven-publish`
}

group = "fr.acinq.eklair"
version = "snapshot"

repositories {
    mavenLocal()
    maven("https://dl.bintray.com/kotlin/kotlinx")
    maven("https://dl.bintray.com/kotlin/ktor")
    maven("https://dl.bintray.com/kodein-framework/kodein-dev")
    maven("https://dl.bintray.com/acinq/libs")
    google()
    jcenter()
}

val currentOs = org.gradle.internal.os.OperatingSystem.current()

kotlin {
    fun ktor(module: String, version: String = "1.4.0") = "io.ktor:ktor-$module:$version"
    val secp256k1Version = "0.3.0"
    val serializationVersion = "1.0.0-RC"

    val commonMain by sourceSets.getting {
        dependencies {
            api("fr.acinq.bitcoink:bitcoink:0.5.0")
            api("fr.acinq.secp256k1:secp256k1:$secp256k1Version")
            api("org.kodein.log:kodein-log:0.5.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9-native-mt")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:$serializationVersion")
        }
    }
    val commonTest by sourceSets.getting {
        dependencies {
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
            implementation(ktor("client-core"))
            implementation(ktor("client-auth"))
            implementation(ktor("client-json"))
            implementation(ktor("client-serialization"))
        }
    }

    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        compilations["main"].defaultSourceSet.dependencies {
            implementation(ktor("client-okhttp"))
            implementation(ktor("network"))
            implementation(ktor("network-tls"))
            implementation("org.slf4j:slf4j-api:1.7.29")
            api("org.xerial:sqlite-jdbc:3.32.3.2")
        }
        compilations["test"].kotlinOptions.jvmTarget = "1.8"
        compilations["test"].defaultSourceSet.dependencies {
            val target = when {
                currentOs.isLinux -> "linux"
                currentOs.isMacOsX -> "darwin"
                currentOs.isWindows -> "mingw"
                else -> error("UnsupportedmOS $currentOs")
            }
            implementation("fr.acinq.secp256k1:secp256k1-jni-jvm-$target:$secp256k1Version")
            implementation(kotlin("test-junit"))
            implementation("org.bouncycastle:bcprov-jdk15on:1.64")
            implementation("ch.qos.logback:logback-classic:1.2.3")
        }
    }

    val nativeMain by sourceSets.creating { dependsOn(commonMain) }
    val nativeTest by sourceSets.creating { dependsOn(commonTest) }

    if (currentOs.isLinux) {
        linuxX64("linux") {
            compilations["main"].defaultSourceSet {
                dependsOn(nativeMain)
            }
            compilations["test"].defaultSourceSet {
                dependsOn(nativeTest)
                dependencies {
                    implementation(ktor("client-curl"))
                }
            }
        }
    }

    if (currentOs.isMacOsX) {
        ios {
            compilations["main"].cinterops.create("ios_network_framework")
            compilations["main"].defaultSourceSet {
                dependsOn(nativeMain)
            }
            compilations["test"].defaultSourceSet {
                dependsOn(nativeTest)
                dependencies {
                    implementation(ktor("client-ios"))
                }
            }
        }
    }

    sourceSets.all {
        languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
        languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
    }
}


// Disable cross compilation
afterEvaluate {
    val targets = when {
        currentOs.isLinux -> listOf()
        else -> listOf("linuxX64")
    }.mapNotNull { kotlin.targets.findByName(it) as? KotlinNativeTarget }

    configure(targets) {
        compilations.all {
            cinterops.all { tasks[interopProcessingTaskName].enabled = false }
            compileKotlinTask.enabled = false
            tasks[processResourcesTaskName].enabled = false
        }
        binaries.all { linkTask.enabled = false }

        mavenPublication {
            val publicationToDisable = this
            tasks.withType<AbstractPublishToMaven>().all { onlyIf { publication != publicationToDisable } }
            tasks.withType<GenerateModuleMetadata>().all { onlyIf { publication.get() != publicationToDisable } }
        }
    }
}

afterEvaluate {
    tasks.withType<AbstractTestTask>() {
        testLogging {
            events("passed", "skipped", "failed", "standard_out", "standard_error")
            showExceptions = true
            showStackTraces = true
        }
    }
}

/*
Electrum integration test environment + tasks configuration
 */
val dockerTestEnv by tasks.creating(Exec::class) {
    workingDir = projectDir
    commandLine("bash", "docker-env.sh")
    doLast {
        gradle.buildFinished {
            exec {
                println("Cleaning up dockers...")
                workingDir = projectDir
                commandLine("bash", "docker-cleanup.sh")
            }
        }
    }
}

val excludeIntegrationTests = project.findProperty("integrationTests") == "exclude"
tasks.withType<AbstractTestTask> {
    if (excludeIntegrationTests) {
        filter.excludeTestsMatching("*IntegrationTest")
    } else {
        dependsOn(dockerTestEnv)
    }
}

// Linux native does not support integration tests (sockets are not implemented in Linux native)
if (currentOs.isLinux) {
    val linuxTest by tasks.getting(KotlinNativeTest::class) {
        filter.excludeTestsMatching("*IntegrationTest")
    }
}