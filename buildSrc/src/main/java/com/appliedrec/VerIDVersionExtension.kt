package com.appliedrec

open class VerIDVersionExtension {

    val versionMajor = 2
    val versionMinor = 14
    val versionPatch = 1
    val versionClassifier: String? = null
    val versionClassifierVersion: String? = null

    val minSdkVersion = 26
    val targetSdkVersion = 34
    val buildToolsVersion = "34"
    val compileSdkVersion = 34
    val kotlinCompilerExtensionVersion = "1.4.2"
    val kotlinJvmTarget = "17"
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