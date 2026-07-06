import org.gradle.plugin.compatibility.compatibility
import java.util.*

plugins {
    `kotlin-dsl`
    alias(libs.plugins.gradle.plugin.publish)
}

group = "com.hiczp"
version = Properties().apply {
    file("../gradle.properties").inputStream().use(::load)
}.getProperty("projectVersion")

repositories {
    gradlePluginPortal()
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.gradle.node.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    website.set("https://github.com/czp3009/kotlin-native-npm-publishing")
    vcsUrl.set("https://github.com/czp3009/kotlin-native-npm-publishing.git")

    plugins {
        create("kotlinNativeNpmPublishing") {
            id = "com.hiczp.kotlin-native-npm-publishing"
            implementationClass = "com.hiczp.kotlin.native.npm.publishing.KotlinNativeNpmPublishingPlugin"
            displayName = "kotlin native npm publishing"
            description = "Publishes executable binaries from kotlin native targets to npm as npx-runnable packages."
            tags.set(listOf("kotlin", "kotlin-native", "npm", "npx", "publishing"))

            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}
