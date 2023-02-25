package com.appliedrec

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency

class VerIDBuildPlugin: Plugin<Project> {

    override fun apply(project: Project) {
//        val library = project.extensions.getByType(LibraryExtension::class.java)
        project.extensions.create("versions", VerIDVersionExtension::class.java)
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        androidComponents.finalizeDsl { extension ->
            extension.compileOptions.sourceCompatibility = JavaVersion.VERSION_1_8
            extension.compileOptions.targetCompatibility = JavaVersion.VERSION_1_8
            when (project.name) {
                "sample" -> {
                    extension.compileOptions.isCoreLibraryDesugaringEnabled = true
                    extension.buildFeatures.viewBinding = true
                    extension.buildFeatures.compose = true
                    extension.composeOptions.kotlinCompilerExtensionVersion = project.extensions.getByType(VerIDVersionExtension::class.java).kotlinCompilerExtensionVersion
                }
                "veridui" -> {
                    extension.compileOptions.isCoreLibraryDesugaringEnabled = true
                    extension.buildFeatures.viewBinding = true
                    extension.buildFeatures.compose = true
                    extension.composeOptions.kotlinCompilerExtensionVersion = project.extensions.getByType(VerIDVersionExtension::class.java).kotlinCompilerExtensionVersion
                }
                "veridcore" -> {
                    extension.compileOptions.isCoreLibraryDesugaringEnabled = true
                }
            }
            extension.lint.abortOnError = false
            extension.packagingOptions.resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            extension.packagingOptions.resources.excludes.add("META-INF/androidx.exifinterface_exifinterface.version")
            when (project.name) {
                "sample" -> {
                    extension.testOptions.execution = "ANDROIDX_TEST_ORCHESTRATOR"
                }
                "veridcore" -> {
                    extension.testOptions.unitTests.isReturnDefaultValues = true
                }
            }

        }
        when (project.name) {
            "veridui" -> {
                project.dependencies.add("coreLibraryDesugaring", VerIDDependencies.DESUGAR_JDK_LIBS)
                project.dependencies.add("api", VerIDDependencies.ESPRESSO_CORE)
                project.dependencies.add("api", VerIDDependencies.RXJAVA)
                project.dependencies.add("api", VerIDDependencies.RXANDROID)
                project.dependencies.add("implementation", VerIDDependencies.VERID_IDENTITY)
                project.dependencies.add("implementation", VerIDDependencies.APPCOMPAT)
                project.dependencies.add("implementation", VerIDDependencies.EXIFINTERFACE)
                project.dependencies.add("implementation", VerIDDependencies.ROOM_RUNTIME)
                project.dependencies.add("implementation", VerIDDependencies.ANNOTATION)
                project.dependencies.add("implementation", VerIDDependencies.GSON)
                project.dependencies.add("implementation", VerIDDependencies.LIFECYCLE_COMMON_JAVA8)
                project.dependencies.add("implementation", VerIDDependencies.CONSTRAINTLAYOUT)
                project.dependencies.add("implementation", VerIDDependencies.DYNAMICANIMATION)
                project.dependencies.add("implementation", VerIDDependencies.COMPOSE_UI)
                project.dependencies.add("implementation", VerIDDependencies.COMPOSE_RUNTIME_LIVEDATA)
                project.dependencies.add("implementation", VerIDDependencies.COMPOSE_MATERIAL3)
                project.dependencies.add("implementation", VerIDDependencies.COMPOSE_LIFECYCLE_VIEWMODEL)
                project.dependencies.add("implementation", VerIDDependencies.COMPOSE_RUNTIME)
                project.dependencies.add("debugImplementation", VerIDDependencies.COMPOSE_TOOLING_PREVIEW)
                project.dependencies.add("androidTestImplementation", VerIDDependencies.TEST_JUNIT)
                project.dependencies.add("androidTestImplementation", VerIDDependencies.TEST_RUNNER)
                project.dependencies.add("androidTestImplementation", VerIDDependencies.TEST_RULES)
                project.dependencies.add("androidTestImplementation", VerIDDependencies.ESPRESSO_CORE)
                project.dependencies.add("androidTestImplementation", VerIDDependencies.ESPRESSO_INTENTS)
            }
            "sample" -> {
                project.dependencies.add("coreLibraryDesugaring", VerIDDependencies.DESUGAR_JDK_LIBS)
                project.dependencies.add("implementation", VerIDDependencies.VERID_SERIALIZATION)?.apply {
                    (this as? ModuleDependency)?.isTransitive = false
                }
                project.dependencies.add("implementation", VerIDDependencies.CONSTRAINTLAYOUT)
                project.dependencies.add("implementation", VerIDDependencies.GUAVA)
                project.dependencies.add("implementation", VerIDDependencies.PREFERENCE)
                project.dependencies.add("implementation", VerIDDependencies.PROTOBUF_JAVALITE)
                project.dependencies.add("implementation", VerIDDependencies.MULTIDEX)
                project.dependencies.add("implementation", VerIDDependencies.ESPRESSO_CORE)
                project.dependencies.add("implementation", VerIDDependencies.COMPOSE_RUNTIME)
                project.dependencies.add("androidTestImplementation", VerIDDependencies.TEST_JUNIT)
                project.dependencies.add("androidTestImplementation", VerIDDependencies.TEST_RUNNER)
                project.dependencies.add("androidTestImplementation", VerIDDependencies.TEST_RULES)
                project.dependencies.add("androidTestImplementation", VerIDDependencies.ESPRESSO_CORE)
                project.dependencies.add("androidTestImplementation", VerIDDependencies.ESPRESSO_INTENTS)
                project.dependencies.add("androidTestUtil", VerIDDependencies.TEST_ORCHESTRATOR)
            }
            "veridcore" -> {
                project.dependencies.add("coreLibraryDesugaring", VerIDDependencies.DESUGAR_JDK_LIBS)
                project.dependencies.add("api", VerIDDependencies.RXJAVA)
                project.dependencies.add("api", VerIDDependencies.RXANDROID)
                project.dependencies.add("api", VerIDDependencies.VERID_IDENTITY)
                project.dependencies.add("api", VerIDDependencies.LIVENESS_DETECTION)
                project.dependencies.add("api", VerIDDependencies.LIFECYCLE_LIVEDATA_CORE)
                project.dependencies.add("implementation", VerIDDependencies.RELINKER)
                project.dependencies.add("implementation", VerIDDependencies.CBOR)
                project.dependencies.add("implementation", VerIDDependencies.GSON)
                project.dependencies.add("implementation", VerIDDependencies.GUAVA)
                project.dependencies.add("implementation", VerIDDependencies.EXIFINTERFACE)
                project.dependencies.add("implementation", VerIDDependencies.TEST_MONITOR)
//                project.dependencies.add("implementation", VerIDDependencies.ROOM_KTX)
                project.dependencies.add("implementation", VerIDDependencies.COMPOSE_RUNTIME)
                project.dependencies.add("implementation", VerIDDependencies.ANDROIDX_COLLECTION)
                project.dependencies.add("implementation", VerIDDependencies.ANDROIDX_CORE)
                project.dependencies.add("implementation", VerIDDependencies.ROOM_RUNTIME)
                project.dependencies.add("testImplementation", VerIDDependencies.JUNIT)
                project.dependencies.add("testImplementation", VerIDDependencies.MOCKITO_CORE)
                project.dependencies.add("testImplementation", VerIDDependencies.MOCKITO_ANDROID)
                project.dependencies.add("androidTestImplementation", VerIDDependencies.EXIFINTERFACE)
                project.dependencies.add("androidTestImplementation", VerIDDependencies.GSON)
                project.dependencies.add("androidTestImplementation", VerIDDependencies.ROOM_TESTING)
                project.dependencies.add("annotationProcessor", VerIDDependencies.ROOM_COMPILER)
                project.dependencies.add("kapt", VerIDDependencies.ROOM_COMPILER)
//                project.dependencies.add("ksp", VerIDDependencies.ROOM_COMPILER)
            }
        }
    }
}