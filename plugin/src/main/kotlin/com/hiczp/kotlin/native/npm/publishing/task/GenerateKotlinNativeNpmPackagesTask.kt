package com.hiczp.kotlin.native.npm.publishing.task

import com.hiczp.kotlin.native.npm.publishing.model.NativeBinarySpec
import com.hiczp.kotlin.native.npm.publishing.model.NpmPlatform
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
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
                    "Define release executable binaries for Linux, macOS, or Windows native targets."
        }

        val outputDir = outputDirectory.get().asFile
        fileSystemOperations.delete {
            delete(outputDir)
        }

        val platformPackageNames = binaries.map { binary ->
            writePlatformPackage(outputDir, packageName, packageVersion, commandName, binary)
        }

        writeMainPackage(outputDir, packageName, packageVersion, commandName, platformPackageNames)
    }

    private fun writePlatformPackage(
        outputDir: File,
        packageName: String,
        packageVersion: String,
        commandName: String,
        binary: NativeBinarySpec,
    ): String {
        val platformPackageName = if (packageName.startsWith("@")) {
            val slashIndex = packageName.indexOf('/')
            require(slashIndex > 1 && slashIndex < packageName.lastIndex) {
                "Scoped npm package names must use the @scope/name form."
            }

            "${packageName.substring(0, slashIndex)}/${packageName.substring(slashIndex + 1)}-${binary.packageSuffix}"
        } else {
            "$packageName-${binary.packageSuffix}"
        }
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

        return platformPackageName
    }

    private fun writeMainPackage(
        outputDir: File,
        packageName: String,
        packageVersion: String,
        commandName: String,
        platformPackageNames: List<String>,
    ) {
        val packageDir = outputDir.resolve("main")
        val binDir = packageDir.resolve("bin")
        binDir.mkdirs()

        val scriptPath = "bin/$commandName.js"
        val script = binDir.resolve("$commandName.js")
        script.writeText(wrapperResource(), Charsets.UTF_8)
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
                    platformPackageNames.associateWith { packageVersion },
                )
            },
        )
    }

    private fun writeJson(file: File, value: Any?) {
        file.parentFile.mkdirs()
        file.writeText("${JsonOutput.prettyPrint(JsonOutput.toJson(value))}\n", Charsets.UTF_8)
    }

    private fun wrapperResource(): String {
        return javaClass.getResource(WRAPPER_RESOURCE)?.readText(Charsets.UTF_8)
            ?: throw GradleException("Missing wrapper resource: $WRAPPER_RESOURCE")
    }

    private companion object {
        const val WRAPPER_RESOURCE = "/com/hiczp/kotlin/native/npm/publishing/wrapper.js"
    }
}

private val NpmPlatform.descriptor: String
    get() = listOf(os, cpu, libc.orEmpty()).joinToString(":")
