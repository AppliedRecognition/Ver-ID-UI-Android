package com.appliedrec

open class VerIDVersionExtension {

    val versionMajor = 2
    val versionMinor = 8
    val versionPatch = 4
    val versionClassifier: String? = null
    val versionClassifierVersion: String? = null

    val minSdkVersion = 24
    val targetSdkVersion = 33
    val buildToolsVersion = "30.0.3"
    val compileSdkVersion = 33
    val kotlinCompilerExtensionVersion = "1.4.2"
    val kotlinJvmTarget = "1.8"
    val kotlinVersion = "1.8.10"

    val versionCode: Int
        get() = minSdkVersion * 10000000 + versionMajor * 10000 + versionMinor * 100 + versionPatch

    val versionName: String
        get() {
            val versionName = StringBuilder()
            versionName.append(versionMajor).append(".").append(versionMinor).append(".")
                .append(versionPatch)
            if (versionClassifier != null) {
                versionName.append("-").append(versionClassifier)
                if (versionClassifierVersion != null) {
                    versionName.append(".").append(versionClassifierVersion)
                }
            }
            return versionName.toString()
        }
}