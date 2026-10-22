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
        mavenCentral()
    }
}
rootProject.name = "GBSat"
include(":app")
// Shared framework GBFrame (git submodule, https://github.com/mindspot7/GBFrame)
include(":gbframe")
project(":gbframe").projectDir = file("../../libs/gbframe/gbframe")
