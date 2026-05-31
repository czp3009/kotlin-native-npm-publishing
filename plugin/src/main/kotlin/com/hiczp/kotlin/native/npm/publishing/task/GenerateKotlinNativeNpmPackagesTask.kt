package com.hiczp.kotlin.native.npm.publishing.task

import com.hiczp.kotlin.native.npm.publishing.model.NativeBinarySpec
import com.hiczp.kotlin.native.npm.publishing.model.NpmPlatform
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "This task assembles temporary npm package directories for immediate publication.")
abstract class GenerateKotlinNativeNpmPackagesTask : DefaultTask() {
    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val packageVersion: Property<String>

    @get:Input
    abstract val commandName: Property<String>

    @get:Optional
    @get:Input
    abstract val packageDescription: Property<String>

    @get:Optional
    @get:Input
    abstract val license: Property<String>

    @get:Optional
    @get:Input
    abstract val repository: Property<String>

    @get:Optional
    @get:Input
    abstract val homepage: Property<String>

    @get:Input
    abstract val keywords: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val executableFiles: ConfigurableFileCollection

    @get:Input
    abstract val platformDescriptors: SetProperty<String>

    @get:Internal
    val binaries: MutableList<NativeBinarySpec> = mutableListOf()

    fun binary(binary: NativeBinarySpec) {
        binaries.add(binary)
        executableFiles.from(binary.executableFile)
        platformDescriptors.add(binary.platform.descriptor)
    }

    @TaskAction
    fun generate() {
        val packageName = packageName.get().trim()
        val packageVersion = packageVersion.get().trim()
        val commandName = commandName.get().trim()
        val binaries = binaries.sortedBy { it.packageSuffix }

        require(packageName.isNotEmpty()) { "npm package name must not be empty." }
        require(packageVersion.isNotEmpty() && packageVersion != "unspecified") {
            "Project version is unspecified. Configure project.version or kotlinNativeNpmPublishing.packageVersion."
        }
        require(commandName.isNotEmpty()) { "npm command name must not be empty." }
        require(binaries.isNotEmpty()) {
            "No supported kotlin native executable link tasks were found. " +
                    "Define native executable binaries for one of: " +
                    NpmPlatform.supportedKotlinNativeTargets.joinToString(", ") + "."
        }

        val outputDir = outputDirectory.get().asFile
        fileSystemOperations.delete {
            delete(outputDir)
        }

        val platformPackages = binaries.map { binary ->
            writePlatformPackage(outputDir, packageName, packageVersion, commandName, binary)
        }

        writeMainPackage(outputDir, packageName, packageVersion, commandName, platformPackages)
    }

    private fun writePlatformPackage(
        outputDir: File,
        packageName: String,
        packageVersion: String,
        commandName: String,
        binary: NativeBinarySpec,
    ): PlatformPackage {
        val platformPackageName = platformPackageName(packageName, binary.packageSuffix)
        val packageDir = outputDir.resolve("platforms/${binary.packageSuffix}")
        val binDir = packageDir.resolve("bin")

        val source = binary.executableFile.get()
        require(source.isFile) {
            "Expected executable output for ${binary.targetName} at ${source.absolutePath}."
        }
        val binaryFileName = if (binary.platform.os == "win32") "$commandName.exe" else commandName
        val binaryPath = "bin/$binaryFileName"
        val target = packageDir.resolve(binaryPath)
        fileSystemOperations.copy {
            from(source) {
                rename { binaryFileName }
            }
            into(binDir)
        }
        if (binary.platform.os != "win32") {
            target.setExecutable(true, false)
        }

        writeJson(
            packageDir.resolve("package.json"),
            buildMap {
                put("name", platformPackageName)
                put("version", packageVersion)
                packageDescription.orNull?.let { put("description", it) }
                license.orNull?.let { put("license", it) }
                put("os", listOf(binary.platform.os))
                put("cpu", listOf(binary.platform.cpu))
                binary.platform.libc?.let { put("libc", listOf(it)) }
                put("files", listOf("bin/"))
                put(
                    "kotlinNativeNpmPublishing",
                    mapOf(
                        "binary" to binaryPath,
                    ),
                )
            },
        )

        return PlatformPackage(
            name = platformPackageName,
            platform = binary.platform,
        )
    }

    private fun writeMainPackage(
        outputDir: File,
        packageName: String,
        packageVersion: String,
        commandName: String,
        platformPackages: List<PlatformPackage>,
    ) {
        val packageDir = outputDir.resolve("main")
        val binDir = packageDir.resolve("bin")
        binDir.mkdirs()

        val scriptPath = "bin/$commandName.js"
        val script = binDir.resolve("$commandName.js")
        script.writeText(wrapperScript(platformPackages), Charsets.UTF_8)
        script.setExecutable(true, false)

        writeJson(
            packageDir.resolve("package.json"),
            buildMap {
                put("name", packageName)
                put("version", packageVersion)
                packageDescription.orNull?.let { put("description", it) }
                license.orNull?.let { put("license", it) }
                repository.orNull?.let { put("repository", it) }
                homepage.orNull?.let { put("homepage", it) }
                val keywords = keywords.get()
                if (keywords.isNotEmpty()) {
                    put("keywords", keywords)
                }
                put("bin", mapOf(commandName to scriptPath))
                put("files", listOf("bin/"))
                put(
                    "optionalDependencies",
                    platformPackages.associate { it.name to packageVersion },
                )
            },
        )
    }

    private fun wrapperScript(platformPackages: List<PlatformPackage>): String {
        val packageMap = platformPackages.joinToString(",\n") { platformPackage ->
            val key = "${platformPackage.platform.os}-${platformPackage.platform.cpu}"
            "  ${jsonString(key)}: ${jsonString(platformPackage.name)}"
        }

        return """
            |#!/usr/bin/env node
            |'use strict';
            |
            |const childProcess = require('child_process');
            |const fs = require('fs');
            |const path = require('path');
            |
            |const packages = {
            |$packageMap
            |};
            |
            |const key = `${'$'}{process.platform}-${'$'}{process.arch}`;
            |const packageName = packages[key];
            |
            |if (!packageName) {
            |  console.error(`Unsupported platform: ${'$'}{key}`);
            |  process.exit(1);
            |}
            |
            |let packageRoot;
            |try {
            |  packageRoot = path.dirname(require.resolve(`${'$'}{packageName}/package.json`));
            |} catch (error) {
            |  console.error(`Missing platform package: ${'$'}{packageName}`);
            |  console.error('Reinstall this npm package on the target platform.');
            |  process.exit(1);
            |}
            |
            |const packageJson = require(path.join(packageRoot, 'package.json'));
            |const binary = path.join(packageRoot, packageJson.kotlinNativeNpmPublishing.binary);
            |
            |if (process.platform !== 'win32') {
            |  try {
            |    fs.chmodSync(binary, 0o755);
            |  } catch (_) {
            |  }
            |}
            |
            |const result = childProcess.spawnSync(binary, process.argv.slice(2), { stdio: 'inherit' });
            |
            |if (result.error) {
            |  console.error(result.error.message);
            |  process.exit(1);
            |}
            |
            |if (result.signal) {
            |  console.error(`Process terminated by signal ${'$'}{result.signal}`);
            |  process.exit(1);
            |}
            |
            |process.exit(result.status === null ? 1 : result.status);
            |
        """.trimMargin()
    }

    private fun platformPackageName(packageName: String, platformSuffix: String): String {
        if (!packageName.startsWith("@")) {
            return "$packageName-$platformSuffix"
        }

        val slashIndex = packageName.indexOf('/')
        require(slashIndex > 1 && slashIndex < packageName.lastIndex) {
            "Scoped npm package names must use the @scope/name form."
        }

        val scope = packageName.substring(0, slashIndex)
        val unscopedName = packageName.substring(slashIndex + 1)
        return "$scope/$unscopedName-$platformSuffix"
    }

    private fun writeJson(file: File, value: Any?) {
        file.parentFile.mkdirs()
        file.writeText("${JsonOutput.prettyPrint(JsonOutput.toJson(value))}\n", Charsets.UTF_8)
    }

    private fun jsonString(value: String): String = JsonOutput.toJson(value)
}

private data class PlatformPackage(
    val name: String,
    val platform: NpmPlatform,
)

private val NpmPlatform.descriptor: String
    get() = listOf(os, cpu, libc.orEmpty()).joinToString(":")
