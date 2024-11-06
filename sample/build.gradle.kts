plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.appliedrec.verid.sample"
    buildToolsVersion = libs.versions.buildToolsVersion.get()
    compileSdk = libs.versions.compileSdkVersion.get().toInt()

    defaultConfig {
        applicationId = "com.appliedrec.verid.sample"
        minSdk = libs.versions.minSdkVersion.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "VERSION_NAME", "\""+versionCatalogs.named("libs").findVersion("verid").get().toString()+"\"")
        multiDexEnabled = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    }

    kotlinOptions {
        jvmTarget = libs.versions.javaVersion.get()
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    packaging {
        jniLibs {
            pickFirsts.add("lib/arm64-v8a/libdetrec.so")
            pickFirsts.add("lib/armeabi-v7a/libdetrec.so")
            pickFirsts.add("lib/x86/libdetrec.so")
            pickFirsts.add("lib/x86_64/libdetrec.so")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isJniDebuggable = false
//            proguardFile "r8-rules.pro"
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isJniDebuggable = true
            isDebuggable = true
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir("src/main/application")
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    api(project(":veridui"))
    implementation(libs.verid.serialization) {
        isTransitive = false
    }
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.constraintlayout)
    implementation(libs.google.guava)
    implementation(libs.androidx.preference)
    implementation(libs.protobuf.javalite)
    implementation(libs.androidx.multidex)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.core.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestUtil(libs.androidx.test.orchestrator)
}
