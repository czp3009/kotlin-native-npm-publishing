package com.hiczp.kotlin.native.npm.publishing.model

import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family

data class NpmPlatform(
    val os: String,
    val cpu: String,
    val libc: String? = null,
) {
    companion object {
        fun fromKonanTarget(family: Family, architecture: Architecture): NpmPlatform? {
            val os = when (family) {
                Family.LINUX -> "linux"
                Family.OSX -> "darwin"
                Family.MINGW -> "win32"
                else -> return null
            }

            val cpu = when (architecture) {
                Architecture.X64 -> "x64"
                Architecture.ARM64 -> "arm64"
                else -> return null
            }

            return NpmPlatform(
                os = os,
                cpu = cpu,
                libc = if (os == "linux") "glibc" else null,
            )
        }
    }
}
