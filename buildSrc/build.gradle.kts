plugins {
    kotlin("jvm") version "1.8.10"
    id("java-gradle-plugin")
    id("groovy-gradle-plugin")
}

repositories {
    google()
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("simplePlugin") {
            id = "com.appliedrec.veridbuild"
            implementationClass = "com.appliedrec.VerIDBuildPlugin"
        }
    }
}

dependencies {
    implementation(kotlin("stdlib", "1.8.10"))
    implementation("com.android.tools.build:gradle-api:7.4.1")
    gradleApi()
}