rootProject.name = "lightning-kmp"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android" || requested.id.name == "kotlin-android-extensions") {
                useModule("com.android.tools.build:gradle:7.4.0")
            }
        }
    }
}

include(
    ":PhoenixCrypto"
)
include(":android-tests")
