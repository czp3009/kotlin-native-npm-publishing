package com.hiczp.kotlin.native.npm.publishing.model

import org.gradle.api.provider.Provider
import java.io.File

data class NativeBinarySpec(
    val targetName: String,
    val platform: NpmPlatform,
    val executableFile: Provider<File>,
) {
    val packageSuffix: String
        get() = platform.packageSuffix
}
