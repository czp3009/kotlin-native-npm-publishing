import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.hiczp.kotlin-native-npm-publishing")
}

kotlin {
    mingwX64()
    linuxX64()
    linuxArm64()
    macosArm64()

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.executable {
            entryPoint = "com.hiczp.kotlin.native.npm.publishing.example.main"
        }
    }
}

kotlinNativeNpmPublishing {
    packageName.set(providers.gradleProperty("npmPackageName"))
    description.set("Local npm publishing example for kotlin native npm publishing.")
    license.set("MIT")
    repository.set("https://github.com/czp3009/kotlin-native-npm-publishing")
    keywords.addAll("kotlin", "kotlin-native", "npm")
    registry.set(providers.gradleProperty("npmRegistry"))
    access.set(providers.gradleProperty("npmAccess"))
    tag.set(providers.gradleProperty("npmTag"))
    otp.set(providers.gradleProperty("npmOtp"))
    provenance.set(providers.gradleProperty("npmProvenance").map(String::toBoolean).orElse(false))
    provenanceFile.set(providers.gradleProperty("npmProvenanceFile"))
    dryRun.set(
        providers.gradleProperty("npmDryRun").map(String::toBoolean)
            .orElse(providers.gradleProperty("npmRegistry").map { false })
            .orElse(providers.gradleProperty("npmAccess").map { false })
            .orElse(true),
    )
}
