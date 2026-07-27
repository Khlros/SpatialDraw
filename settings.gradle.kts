pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://artifact.bytedance.com/repository/Volcengine")
            name = "VolcengineMaven"
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://artifact.bytedance.com/repository/Volcengine")
            name = "VolcengineMaven"
        }
    }
}

rootProject.name = "Drawer"
include(":app")
