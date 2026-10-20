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
// Shared UI kit (git submodule, https://github.com/mindspot7/UI_GB)
include(":uikit")
project(":uikit").projectDir = file("../../libs/ui_gb/uikit")
