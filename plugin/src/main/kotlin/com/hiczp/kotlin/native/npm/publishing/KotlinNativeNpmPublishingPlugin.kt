package com.hiczp.kotlin.native.npm.publishing

import com.github.gradle.node.npm.task.NpmTask
import com.hiczp.kotlin.native.npm.publishing.task.FinalizeKotlinNativeNpmMainPackageTask
import com.hiczp.kotlin.native.npm.publishing.task.FinalizeKotlinNativeNpmPlatformPackageTask
import com.hiczp.kotlin.native.npm.publishing.task.PrepareKotlinNativeNpmMainPackageTask
import com.hiczp.kotlin.native.npm.publishing.task.PrepareKotlinNativeNpmPlatformPackageTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

abstract class KotlinNativeNpmPublishingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("com.github.node-gradle.node")

        val extension = project.extensions.create(
            "kotlinNativeNpmPublishing",
            KotlinNativeNpmPublishingExtension::class.java,
        )

        val mainPackageDirectory = extension.stage.outputDirectory.dir("main")
        val prepareMainPackage = project.tasks.register(
            "prepareKotlinNativeNpmMainPackage",
            PrepareKotlinNativeNpmMainPackageTask::class.java,
        ) {
            group = "build"
            description = "Prepares the main npm package stage directory."
            commandName.set(extension.commandName)
            stageCopySpecs.set(extension.stage.main.copySpecs)
            packageDirectory.set(mainPackageDirectory)
        }

        val finalizeMainPackage = project.tasks.register(
            "finalizeKotlinNativeNpmMainPackage",
            FinalizeKotlinNativeNpmMainPackageTask::class.java,
        ) {
            group = "build"
            description = "Finalizes the main npm package stage directory."
            dependsOn(prepareMainPackage)
            packageMetadata.packageName.set(extension.packageName)
            packageMetadata.packageVersion.set(extension.packageVersion)
            commandName.set(extension.commandName)
            packageMetadata.description.set(extension.description)
            packageMetadata.license.set(extension.license)
            packageMetadata.repository.set(extension.repository)
            packageMetadata.homepage.set(extension.homepage)
            packageMetadata.keywords.set(extension.keywords)
            packageDirectory.set(mainPackageDirectory)
        }

        val generateMainPackage = project.tasks.register("generateKotlinNativeNpmMainPackage") {
            group = "build"
            description = "Generates the main npm package stage directory."
            dependsOn(finalizeMainPackage)
        }

        val generatePackages = project.tasks.register("generateKotlinNativeNpmPackages") {
            group = "build"
            description = "Generates npm package directories for kotlin native targets buildable on this host."
            dependsOn(generateMainPackage)
        }

        val publishArguments = project.provider {
            buildList {
                extension.registry.orNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    addAll(listOf("--registry", it))
                }
                extension.access.orNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    addAll(listOf("--access", it))
                }
                extension.tag.orNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    addAll(listOf("--tag", it))
                }
                extension.otp.orNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    addAll(listOf("--otp", it))
                }
                if (extension.dryRun.get()) {
                    add("--dry-run")
                }
                if (extension.provenance.get()) {
                    add("--provenance")
                }
                extension.provenanceFile.orNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    addAll(listOf("--provenance-file", it))
                }
                addAll(extension.publishArguments.get())
            }
        }

        val mainPublishTask = project.registerNpmPublishTask(
            taskName = "publishKotlinNativeNpmMainPackage",
            packageDirectory = extension.stage.outputDirectory.file("main"),
            publishArguments = publishArguments,
            dependsOn = listOf(finalizeMainPackage),
            description = "Publishes the main npm package.",
        )

        val publishTask = project.tasks.register("publishKotlinNativeNpm") {
            group = "publishing"
            description = "Publishes the main npm package and platform packages buildable on this host."
            dependsOn(mainPublishTask)
        }

        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val registeredTargets = mutableSetOf<String>()
            val hostManager = HostManager()

            project.extensions.configure(KotlinMultiplatformExtension::class.java) {
                targets.withType(KotlinNativeTarget::class.java).configureEach {
                    val target = this
                    val konanTarget = target.konanTarget
                    val targetTaskName = target.name.replaceFirstChar { it.uppercaseChar() }
                    if (konanTarget.npmOs == null || konanTarget.npmCpu !in setOf("x64", "arm64")) {
                        return@configureEach
                    }

                    target.binaries.withType(Executable::class.java).configureEach {
                        val binary = this

                        if (binary.buildType == NativeBuildType.RELEASE) {
                            if (!registeredTargets.add(konanTarget.name)) {
                                throw GradleException(
                                    "Multiple release executable binaries were found for ${target.name}. This plugin publishes one executable per kotlin native target.",
                                )
                            }

                            finalizeMainPackage.configure {
                                targetNames.add(konanTarget.name)
                            }

                            val executableOutputFile =
                                project.layout.file(binary.linkTaskProvider.flatMap { it.outputFile })
                            val platformPackageDirectory =
                                extension.stage.outputDirectory.dir("platforms/${konanTarget.name}")
                            val preparePlatformPackage = project.tasks.register(
                                "prepareKotlinNativeNpm${targetTaskName}Package",
                                PrepareKotlinNativeNpmPlatformPackageTask::class.java,
                            ) {
                                group = "build"
                                description = "Prepares the ${target.name} npm platform package stage directory."
                                targetName.set(konanTarget.name)
                                executableFile.set(executableOutputFile)
                                stageCopySpecs.set(extension.stage.platforms.copySpecs)
                                packageDirectory.set(platformPackageDirectory)
                                dependsOn(binary.linkTaskProvider)
                            }

                            val finalizePlatformPackage = project.tasks.register(
                                "finalizeKotlinNativeNpm${targetTaskName}Package",
                                FinalizeKotlinNativeNpmPlatformPackageTask::class.java,
                            ) {
                                group = "build"
                                description = "Finalizes the ${target.name} npm platform package stage directory."
                                dependsOn(preparePlatformPackage)
                                packageMetadata.packageName.set(extension.packageName)
                                packageMetadata.packageVersion.set(extension.packageVersion)
                                packageMetadata.description.set(extension.description)
                                packageMetadata.license.set(extension.license)
                                packageMetadata.repository.set(extension.repository)
                                packageMetadata.homepage.set(extension.homepage)
                                packageMetadata.keywords.set(extension.keywords)
                                targetName.set(konanTarget.name)
                                binaryPath.set(executableOutputFile.map { "bin/${it.asFile.name}" })
                                packageDirectory.set(platformPackageDirectory)
                            }

                            val generatePlatformPackage = project.tasks.register(
                                "generateKotlinNativeNpm${targetTaskName}Package",
                            ) {
                                group = "build"
                                description = "Generates the ${target.name} npm platform package stage directory."
                                dependsOn(finalizePlatformPackage)
                            }

                            val platformPublishTask = project.registerNpmPublishTask(
                                taskName = "publishKotlinNativeNpm${targetTaskName}Package",
                                packageDirectory = extension.stage.outputDirectory.file("platforms/${konanTarget.name}"),
                                publishArguments = publishArguments,
                                dependsOn = listOf(finalizePlatformPackage),
                                description = "Publishes the ${target.name} npm platform package.",
                            )

                            if (hostManager.isEnabled(konanTarget)) {
                                generatePackages.configure {
                                    dependsOn(generatePlatformPackage)
                                }

                                publishTask.configure {
                                    dependsOn(platformPublishTask)
                                }
                            }

                            mainPublishTask.configure {
                                mustRunAfter(platformPublishTask)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Project.registerNpmPublishTask(
        taskName: String,
        packageDirectory: Provider<RegularFile>,
        publishArguments: Provider<out Iterable<String>>,
        dependsOn: Iterable<Any>,
        description: String,
    ): TaskProvider<NpmTask> {
        return tasks.register(taskName, NpmTask::class.java) {
            group = "publishing"
            this.description = description
            this.dependsOn(dependsOn)
            workingDir.set(packageDirectory)
            npmCommand.set(listOf("publish"))
            args.set(publishArguments)
        }
    }

}

internal val KonanTarget.npmCpu: String
    get() = architecture.name.lowercase()

internal val KonanTarget.npmOs: String?
    get() = when (family) {
        Family.LINUX -> "linux"
        Family.OSX -> "darwin"
        Family.MINGW -> "win32"
        else -> null
    }
