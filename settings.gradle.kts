rootProject.name = "syfosmpapirmottak"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.apollographql.apollo") {
                useModule("com.apollographql.apollo:apollo-gradle-plugin:${requested.version}")
            }
        }
    }
}