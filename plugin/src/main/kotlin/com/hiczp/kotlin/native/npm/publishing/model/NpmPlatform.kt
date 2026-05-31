package com.hiczp.kotlin.native.npm.publishing.model

data class NpmPlatform(
    val os: String,
    val cpu: String,
    val libc: String? = null,
) {
    val packageSuffix: String
        get() = listOf(os, cpu).joinToString("-")

    companion object {
        val supportedKotlinNativeTargets = listOf(
            "linuxX64",
            "linuxArm64",
            "macosX64",
            "macosArm64",
            "mingwX64",
        )

        fun fromKotlinNativeTarget(targetName: String): NpmPlatform? {
            return when (targetName) {
                "linuxX64" -> NpmPlatform(os = "linux", cpu = "x64", libc = "glibc")
                "linuxArm64" -> NpmPlatform(os = "linux", cpu = "arm64", libc = "glibc")
                "macosX64" -> NpmPlatform(os = "darwin", cpu = "x64")
                "macosArm64" -> NpmPlatform(os = "darwin", cpu = "arm64")
                "mingwX64" -> NpmPlatform(os = "win32", cpu = "x64")
                else -> null
            }
        }
    }
}
