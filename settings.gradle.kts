pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral() // з Chaquopy 12.0.1+ усе береться звідси, окремий репозиторій chaquo.com не потрібен
    }
}
rootProject.name = "lgram"
include(":app")
