package com.hiczp.kotlin.native.npm.publishing.task

import com.hiczp.kotlin.native.npm.publishing.npmCpu
import com.hiczp.kotlin.native.npm.publishing.npmOs
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "This task assembles a temporary npm package directory for immediate publication.")
abstract class GenerateKotlinNativeNpmMainPackageTask : DefaultTask() {
    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:Nested
    abstract val packageMetadata: KotlinNativeNpmPackageMetadata

    @get:Input
    abstract val commandName: Property<String>

    @get:Input
    abstract val targetNames: SetProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        targetNames.convention(emptySet())
    }

    @TaskAction
    fun generate() {
        val packageName = packageMetadata.packageName.get().trim()
        val packageVersion = packageMetadata.packageVersion.get().trim()
        val commandName = commandName.get().trim()
        val targets = targetNames.get().map {
            requireNotNull(KonanTarget.predefinedTargets[it]) { "Unknown kotlin native target: $it." }
        }.sortedBy { it.name }

        requireValidPackageName(packageName)
        require(packageVersion.isNotEmpty() && packageVersion != "unspecified") {
            "Project version is unspecified. Configure project.version or kotlinNativeNpmPublishing.packageVersion."
        }
        require(commandName.isNotEmpty()) { "npm command name must not be empty." }

        val packageDir = outputDirectory.get().asFile
        fileSystemOperations.delete {
            delete(packageDir)
        }

        val binDir = packageDir.resolve("bin")
        binDir.mkdirs()

        val scriptPath = "bin/$commandName.js"
        val script = binDir.resolve("$commandName.js")
        val wrapperResource = "/com/hiczp/kotlin/native/npm/publishing/wrapper.js"
        script.writeText(
            javaClass.getResource(wrapperResource)?.readText(Charsets.UTF_8)
                ?: throw GradleException("Missing wrapper resource: $wrapperResource"),
            Charsets.UTF_8,
        )
        script.setExecutable(true, false)

        writeJson(
            packageDir.resolve("package.json"),
            buildMap {
                val platformPackages = targets.associate {
                    "${requireNotNull(it.npmOs) { "Unsupported kotlin native target: ${it.name}." }}-${it.npmCpu}" to platformPackageName(
                        packageName,
                        it.name
                    )
                }

                put("name", packageName)
                put("version", packageVersion)
                packageMetadata.description.orNull?.let { put("description", it) }
                packageMetadata.license.orNull?.let { put("license", it) }
                packageMetadata.repository.orNull?.trim()?.trimEnd('/')?.let { repositoryUrl ->
                    put(
                        "repository",
                        mapOf(
                            "type" to "git",
                            "url" to when {
                                repositoryUrl.startsWith("git+") -> repositoryUrl
                                repositoryUrl.startsWith("https://github.com/") && !repositoryUrl.endsWith(".git") -> "git+$repositoryUrl.git"
                                repositoryUrl.startsWith("https://github.com/") -> "git+$repositoryUrl"
                                else -> repositoryUrl
                            },
                        ),
                    )
                }
                packageMetadata.homepage.orNull?.let { put("homepage", it) }
                val keywords = packageMetadata.keywords.get()
                if (keywords.isNotEmpty()) {
                    put("keywords", keywords)
                }
                put("bin", mapOf(commandName to scriptPath))
                put("files", listOf("bin/"))
                put("kotlinNativeNpmPublishing", mapOf("platformPackages" to platformPackages))
                if (platformPackages.isNotEmpty()) {
                    put("optionalDependencies", platformPackages.values.associateWith { packageVersion })
                }
            },
        )
    }
}

@DisableCachingByDefault(because = "This task assembles a temporary npm package directory for immediate publication.")
abstract class GenerateKotlinNativeNpmPlatformPackageTask : DefaultTask() {
    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:Nested
    abstract val packageMetadata: KotlinNativeNpmPackageMetadata

    @get:Input
    abstract val targetName: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val executableFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val packageName = packageMetadata.packageName.get().trim()
        val packageVersion = packageMetadata.packageVersion.get().trim()
        val konanTarget = requireNotNull(KonanTarget.predefinedTargets[targetName.get()]) {
            "Unknown kotlin native target: ${targetName.get()}."
        }
        val npmOs = requireNotNull(konanTarget.npmOs) { "Unsupported kotlin native target: ${konanTarget.name}." }

        requireValidPackageName(packageName)
        require(packageVersion.isNotEmpty() && packageVersion != "unspecified") {
            "Project version is unspecified. Configure project.version or kotlinNativeNpmPublishing.packageVersion."
        }

        val packageDir = outputDirectory.get().asFile
        fileSystemOperations.delete {
            delete(packageDir)
        }

        val source = executableFile.get().asFile
        require(source.isFile) { "Expected executable output for ${konanTarget.name} at ${source.absolutePath}." }
        val binaryPath = "bin/${source.name}"
        val target = packageDir.resolve(binaryPath)
        fileSystemOperations.copy {
            from(source)
            into(packageDir.resolve("bin"))
        }
        target.setExecutable(true, false)

        writeJson(
            packageDir.resolve("package.json"),
            buildMap {
                put("name", platformPackageName(packageName, konanTarget.name))
                put("version", packageVersion)
                packageMetadata.description.orNull?.let { put("description", it) }
                packageMetadata.license.orNull?.let { put("license", it) }
                put("os", listOf(npmOs))
                put("cpu", listOf(konanTarget.npmCpu))
                put("files", listOf("bin/"))
                put("kotlinNativeNpmPublishing", mapOf("binary" to binaryPath))
            },
        )
    }
}

abstract class KotlinNativeNpmPackageMetadata {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val packageVersion: Property<String>

    @get:Optional
    @get:Input
    abstract val description: Property<String>

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
}

private fun platformPackageName(packageName: String, targetName: String): String {
    if (!packageName.startsWith("@")) {
        return "$packageName-$targetName"
    }

    val slashIndex = packageName.indexOf('/')
    return "${packageName.substring(0, slashIndex)}/${packageName.substring(slashIndex + 1)}-$targetName"
}

private fun requireValidPackageName(packageName: String) {
    require(packageName.isNotEmpty()) { "npm package name must not be empty." }
    if (packageName.startsWith("@")) {
        val slashIndex = packageName.indexOf('/')
        require(slashIndex > 1 && slashIndex < packageName.lastIndex) {
            "Scoped npm package names must use the @scope/name form."
        }
    }
}

private fun writeJson(file: File, value: Any?) {
    file.parentFile.mkdirs()
    file.writeText("${JsonOutput.prettyPrint(JsonOutput.toJson(value))}\n", Charsets.UTF_8)
}
