// Maven Central이 429(요청 과다)를 자주 돌려줘서 구글의 Maven Central 미러를 먼저 쓴다
val centralMirror = "https://maven-central.storage-download.googleapis.com/maven2/"

pluginManagement {
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); maven(centralMirror); mavenCentral() }
}
rootProject.name = "HealthBridge"
include(":app")
