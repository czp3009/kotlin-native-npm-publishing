package com.hiczp.kotlin.native.npm.publishing

import com.github.gradle.node.npm.task.NpmTask
import com.hiczp.kotlin.native.npm.publishing.model.NativeBinarySpec
import com.hiczp.kotlin.native.npm.publishing.model.NpmPlatform
import com.hiczp.kotlin.native.npm.publishing.task.GenerateKotlinNativeNpmPackagesTask
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

abstract class KotlinNativeNpmPublishingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("com.github.node-gradle.node")

        val extension = project.extensions.create(
            "kotlinNativeNpmPublishing",
            KotlinNativeNpmPublishingExtension::class.java,
        )

        val generatePackages = project.tasks.register(
            "generateKotlinNativeNpmPackages",
            GenerateKotlinNativeNpmPackagesTask::class.java,
        ) {
            packageName.set(extension.packageName)
            packageVersion.set(extension.packageVersion)
            commandName.set(extension.commandName)
            packageDescription.set(extension.description)
            license.set(extension.license)
            repository.set(extension.repository)
            homepage.set(extension.homepage)
            keywords.set(extension.keywords)
            outputDirectory.set(extension.outputDirectory)
        }

        val mainPublishTask = project.registerNpmPublishTask(
            taskName = "publishKotlinNativeNpmMainPackage",
            packageDirectory = extension.outputDirectory.file("main"),
            publishArguments = extension.publishArguments,
            dependsOn = listOf(generatePackages),
        )

        project.tasks.register("publishKotlinNativeNpm") {
            group = "publishing"
            description = "Publishes executable npm packages for kotlin native targets."
            dependsOn(mainPublishTask)
        }

        project.configureKotlinNativeBinaries(extension, generatePackages, mainPublishTask)
    }

    private fun Project.registerNpmPublishTask(
        taskName: String,
        packageDirectory: Provider<RegularFile>,
        publishArguments: Provider<out Iterable<String>>,
        dependsOn: Iterable<Any>,
    ): TaskProvider<NpmTask> {
        return tasks.register(taskName, NpmTask::class.java) {
            group = null
            description = null
            this.dependsOn(dependsOn)
            workingDir.set(packageDirectory)
            npmCommand.set(listOf("publish"))
            args.set(publishArguments)
        }
    }

    private fun Project.configureKotlinNativeBinaries(
        extension: KotlinNativeNpmPublishingExtension,
        generatePackages: TaskProvider<GenerateKotlinNativeNpmPackagesTask>,
        mainPublishTask: TaskProvider<NpmTask>,
    ) {
        pluginManager.withPlugin(KOTLIN_MULTIPLATFORM_PLUGIN_ID) {
            val registeredPackageSuffixes = mutableSetOf<String>()

            extensions.configure(KotlinMultiplatformExtension::class.java) {
                targets.withType(KotlinNativeTarget::class.java).configureEach {
                    val target = this
                    val platform = NpmPlatform.fromKotlinNativeTarget(target.name)
                        ?: return@configureEach

                    target.binaries.withType(Executable::class.java).configureEach {
                        val binary = this

                        if (binary.buildType == NativeBuildType.RELEASE) {
                            val nativeBinary = NativeBinarySpec(
                                targetName = target.name,
                                platform = platform,
                                executableFile = binary.linkTaskProvider.flatMap { it.outputFile },
                            )

                            if (!registeredPackageSuffixes.add(nativeBinary.packageSuffix)) {
                                throw GradleException(
                                    "Multiple release executable binaries were found for ${target.name}. " +
                                            "This plugin publishes one executable per kotlin native target.",
                                )
                            }

                            generatePackages.configure {
                                binary(nativeBinary)
                                dependsOn(binary.linkTaskProvider)
                            }

                            val platformPublishTask = registerNpmPublishTask(
                                taskName = "publishKotlinNativeNpm${target.name.capitalized()}Package",
                                packageDirectory = extension.outputDirectory
                                    .file("platforms/${nativeBinary.packageSuffix}"),
                                publishArguments = extension.publishArguments,
                                dependsOn = listOf(generatePackages),
                            )

                            mainPublishTask.configure {
                                dependsOn(platformPublishTask)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }

    private companion object {
        const val KOTLIN_MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"
    }
}
