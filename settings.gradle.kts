pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven { url = uri("https://plugins.gradle.org/m2/") }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://s01.oss.sonatype.org/content/groups/staging/") }
        google()
        flatDir {
            dirs("veridui/libs")
        }
    }
}
include(":veridui", ":sample")
