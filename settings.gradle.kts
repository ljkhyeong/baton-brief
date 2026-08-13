pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "baton-brief"

include("domain")
include("application")
include("adapter-in-web")
include("adapter-out-persistence")
include("bootstrap")
