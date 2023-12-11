pluginManagement {
    repositories {
        //noinspection JcenterRepositoryObsolete
        jcenter()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        //noinspection JcenterRepositoryObsolete
        jcenter()
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "Keyspace"
include(":app")
