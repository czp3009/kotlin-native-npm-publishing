package com.hiczp.kotlin.native.npm.publishing

import groovy.json.JsonSlurper
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StageDslTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val projectDir: File
        get() = temporaryFolder.root

    private var usesKotlinPlugin = false

    @Test
    fun `license discovery follows filesystem changes with configuration cache reused`() {
        setup(
            """
            plugins { id("com.hiczp.kotlin-native-npm-publishing") }
            version = "1.0.0"
            kotlinNativeNpmPublishing { stage { main { license() } } }
        """
        )
        write("LICENSE", "plain license")
        write("LICENSE.txt", "text license")
        run("generateKotlinNativeNpmMainPackage").build()
        val output = file("build/kotlinNativeNpmPublishing/main")
        assertEquals("plain license", output.resolve("LICENSE").readText())
        assertEquals("text license", output.resolve("LICENSE.txt").readText())
        assertTrue(packageFiles(output).containsAll(listOf("LICENSE", "LICENSE.txt")))

        assertTrue(file("LICENSE").delete())
        assertTrue(run("generateKotlinNativeNpmMainPackage").build().output.contains("Reusing configuration cache."))
        assertFalse(output.resolve("LICENSE").exists())
        assertEquals("text license", output.resolve("LICENSE.txt").readText())
        assertFalse(packageFiles(output).contains("LICENSE"))

        write("LICENSE", "new license")
        assertTrue(file("LICENSE.txt").delete())
        run("generateKotlinNativeNpmMainPackage").build()
        assertEquals("new license", output.resolve("LICENSE").readText())
        assertFalse(output.resolve("LICENSE.txt").exists())

        assertTrue(file("LICENSE").delete())
        assertTrue(run("generateKotlinNativeNpmMainPackage").buildAndFail().output.contains("No stage copy source exists"))
        assertTrue(file("LICENSE.txt").mkdir())
        assertTrue(run("generateKotlinNativeNpmMainPackage").buildAndFail().output.contains("Stage copy source must be a file"))
    }

    @Test
    fun `explicit license source is renamed and copy destinations cannot escape the package`() {
        setup(
            """
            plugins { id("com.hiczp.kotlin-native-npm-publishing") }
            version = "1.0.0"
            kotlinNativeNpmPublishing {
                stage { main { license("custom-license.txt") } }
            }
        """
        )
        write("custom-license.txt", "custom license")
        run("generateKotlinNativeNpmMainPackage").build()
        val output = file("build/kotlinNativeNpmPublishing/main")
        assertEquals("custom license", output.resolve("LICENSE").readText())
        assertFalse(output.resolve("custom-license.txt").exists())

        file("build.gradle.kts").appendText(
            "\n" + """

            kotlinNativeNpmPublishing {
                stage { main { copy("custom-license.txt", "../escaped.txt") } }
            }
        """.trimIndent()
        )
        val result = run("generateKotlinNativeNpmMainPackage").buildAndFail()
        assertTrue(result.output.contains("Stage copy destination path must stay inside the package directory"))
        assertFalse(output.parentFile.resolve("escaped.txt").exists())
    }

    @Test
    fun `ordinary copy still requires exactly one existing source`() {
        setup(
            """
            plugins { id("com.hiczp.kotlin-native-npm-publishing") }
            version = "1.0.0"
            kotlinNativeNpmPublishing {
                stage { main { copy(files("first.txt", "second.txt")) } }
            }
        """
        )
        write("first.txt", "first")
        write("second.txt", "second")
        assertTrue(
            run("generateKotlinNativeNpmMainPackage").buildAndFail().output.contains(
                "Stage copy source must resolve to exactly one file or directory",
            )
        )
        write(
            "build.gradle.kts",
            file("build.gradle.kts").readText().replace("files(\"first.txt\", \"second.txt\")", "\"missing.txt\"")
        )
        assertTrue(run("generateKotlinNativeNpmMainPackage").buildAndFail().output.contains("Stage copy source does not exist"))
    }

    @Test
    fun `target rules override common rules and stay isolated even with custom target names`() {
        setup(
            kmpBuild(
                """
            linuxX64("customLinux")
            linuxArm64()
            macosArm64()
            macosX64()
            mingwX64()
        """
            ) + """
            // Realize tasks first: later DSL rules must still reach their inputs.
            tasks.withType<com.hiczp.kotlin.native.npm.publishing.task.PrepareKotlinNativeNpmPlatformPackageTask>().forEach { }
            tasks.register<Copy>("generatedResource") {
                from("generated.txt")
                into(layout.buildDirectory.dir("generated"))
            }
            kotlinNativeNpmPublishing {
                stage {
                    platforms {
                        linuxX64 { copy("linux_x64.txt", "selected.txt") }
                        linuxArm64 { copy("linux_arm64.txt", "selected.txt") }
                        macosArm64 { copy("macos_arm64.txt", "selected.txt") }
                        mingwX64 { copy("mingw_x64.txt", "selected.txt") }
                        target(org.jetbrains.kotlin.konan.target.KonanTarget.MACOS_X64) {
                            copy("macos_x64.txt", "selected.txt")
                        }
                        copy("common.txt", "selected.txt")
                        target(org.jetbrains.kotlin.konan.target.KonanTarget.LINUX_X64) {
                            copy(tasks.named<Copy>("generatedResource").map { it.destinationDir.resolve("generated.txt") })
                            copy("common.txt", "last.txt")
                        }
                        linuxX64 { copy("linux_x64.txt", "last.txt") }
                    }
                }
            }
            tasks.register("checkStages") {
                dependsOn(tasks.matching { it.name.startsWith("generateKotlinNativeNpm") && it.name.endsWith("Package") })
            }
        """
        )
        val targets = listOf("linux_x64", "linux_arm64", "macos_arm64", "macos_x64", "mingw_x64")
        targets.forEach { write("$it.txt", it) }
        write("common.txt", "common")
        write("generated.txt", "generated resource")

        val first = run("checkStages").build()
        assertNotNull(first.task(":generatedResource"))
        assertTrue(run("checkStages").build().output.contains("Reusing configuration cache."))
        targets.forEach { target ->
            val output = file("build/kotlinNativeNpmPublishing/platforms/$target")
            assertEquals(target, output.resolve("selected.txt").readText())
            assertTrue(packageFiles(output).contains("selected.txt"))
            assertEquals(target == "linux_x64", output.resolve("generated.txt").exists())
            assertEquals(target == "linux_x64", output.resolve("last.txt").exists())
        }
        val linux = file("build/kotlinNativeNpmPublishing/platforms/linux_x64")
        assertEquals("generated resource", linux.resolve("generated.txt").readText())
        assertEquals("linux_x64", linux.resolve("last.txt").readText())
        assertFalse(file("build/kotlinNativeNpmPublishing/main/selected.txt").exists())
    }

    @Test
    fun `unused platform rules neither create tasks nor resolve missing files`() {
        setup(
            kmpBuild("mingwX64()") + """
            kotlinNativeNpmPublishing {
                stage {
                    platforms {
                        mingwX64 { copy("fake-binary", "matched.txt") }
                        linuxX64 { copy("missing-linux-x64") }
                        linuxArm64 { readme("missing-linux-arm64") }
                        macosArm64 { license("missing-macos-arm64") }
                        target(org.jetbrains.kotlin.konan.target.KonanTarget.MACOS_X64) { copy("missing-macos-x64") }
                        target(org.jetbrains.kotlin.konan.target.KonanTarget.IOS_ARM64) { copy("missing-ios") }
                    }
                }
            }
        """
        )
        val result = run(
            "generateKotlinNativeNpmMainPackage",
            "generateKotlinNativeNpmMingwX64Package",
            "tasks",
            "--all"
        ).build()
        assertFalse(result.output.contains("publishKotlinNativeNpmLinuxX64Package"))
        assertFalse(result.output.contains("publishKotlinNativeNpmMacosX64Package"))
        assertTrue(result.output.contains("publishKotlinNativeNpmMingwX64Package"))
        val platforms = file("build/kotlinNativeNpmPublishing/platforms")
        assertEquals(listOf("mingw_x64"), platforms.listFiles()!!.map { it.name })
        assertEquals("fixture executable", platforms.resolve("mingw_x64/matched.txt").readText())
    }

    private fun kmpBuild(targets: String) = """
        plugins {
            id("com.hiczp.kotlin-native-npm-publishing")
            id("org.jetbrains.kotlin.multiplatform")
        }
        version = "1.0.0"
        kotlin {
            $targets
            targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
                binaries.executable()
            }
        }
        // Exercise staging on every host without requiring cross-compilation toolchains.
        tasks.withType<com.hiczp.kotlin.native.npm.publishing.task.PrepareKotlinNativeNpmPlatformPackageTask>().configureEach {
            setDependsOn(emptyList<Any>())
            executableFile.set(layout.projectDirectory.file("fake-binary"))
        }
        tasks.withType<com.hiczp.kotlin.native.npm.publishing.task.FinalizeKotlinNativeNpmPlatformPackageTask>().configureEach {
            binaryPath.set("bin/fake-binary")
        }
    """.also { usesKotlinPlugin = true }

    private fun setup(build: String) {
        write("settings.gradle.kts", "rootProject.name = \"stage-test\"")
        write("gradle.properties", "kotlin.native.ignoreDisabledTargets=true")
        write("build.gradle.kts", build.trimIndent())
        write("fake-binary", "fixture executable")
    }

    private fun run(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .also { runner ->
            if (usesKotlinPlugin) {
                val kotlinClasspath = System.getProperty("test.kotlinClasspath").split(File.pathSeparator).map(::File)
                runner.withPluginClasspath(runner.pluginClasspath + kotlinClasspath)
            }
        }
        .withArguments(*arguments, "--configuration-cache", "--configuration-cache-problems=fail", "--stacktrace")

    private fun file(path: String) = projectDir.resolve(path)

    private fun write(path: String, content: String) = file(path).writeText(content)

    private fun packageFiles(directory: File): List<*> =
        (JsonSlurper().parse(directory.resolve("package.json")) as Map<*, *>)["files"] as List<*>
}
