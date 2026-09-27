package com.hiczp.kotlin.native.npm.publishing

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.konan.target.KonanTarget
import javax.inject.Inject

abstract class KotlinNativeNpmPublishingExtension @Inject constructor(project: Project) {
    val packageName: Property<String> = project.objects.property(String::class.java)
        .convention(project.name)

    val packageVersion: Property<String> = project.objects.property(String::class.java)
        .convention(project.provider { project.version.toString() })

    val commandName: Property<String> = project.objects.property(String::class.java)
        .convention(packageName.map { it.substringAfter('/').removePrefix("@") })

    val description: Property<String> = project.objects.property(String::class.java)

    val license: Property<String> = project.objects.property(String::class.java)

    val repository: Property<String> = project.objects.property(String::class.java)

    val homepage: Property<String> = project.objects.property(String::class.java)

    val keywords: ListProperty<String> = project.objects.listProperty(String::class.java)
        .convention(emptyList())

    val registry: Property<String> = project.objects.property(String::class.java)

    val access: Property<String> = project.objects.property(String::class.java)

    val tag: Property<String> = project.objects.property(String::class.java)

    val otp: Property<String> = project.objects.property(String::class.java)

    val dryRun: Property<Boolean> = project.objects.property(Boolean::class.javaObjectType)
        .convention(false)

    val provenance: Property<Boolean> = project.objects.property(Boolean::class.javaObjectType)
        .convention(false)

    val provenanceFile: Property<String> = project.objects.property(String::class.java)

    val publishArguments: ListProperty<String> = project.objects.listProperty(String::class.java)
        .convention(emptyList())

    val stage: KotlinNativeNpmStage = project.objects.newInstance(
        KotlinNativeNpmStage::class.java,
        project,
    )

    fun stage(action: Action<in KotlinNativeNpmStage>) {
        action.execute(stage)
    }
}

abstract class KotlinNativeNpmStage @Inject constructor(project: Project) {
    val outputDirectory: DirectoryProperty = project.objects.directoryProperty()
        .convention(project.layout.buildDirectory.dir("kotlinNativeNpmPublishing"))

    val main: KotlinNativeNpmPackageStageSpec = project.objects.newInstance(
        KotlinNativeNpmPackageStageSpec::class.java,
        project,
    )

    // Gradle decoration would eagerly resolve KonanTarget even when KMP is not on the classpath.
    val platforms = KotlinNativeNpmPlatformsStageSpec(project)

    fun main(action: Action<in KotlinNativeNpmPackageStageSpec>) {
        action.execute(main)
    }

    fun platforms(action: Action<in KotlinNativeNpmPlatformsStageSpec>) {
        action.execute(platforms)
    }
}

class KotlinNativeNpmPlatformsStageSpec internal constructor(private val project: Project) :
    KotlinNativeNpmPackageStageSpec(project) {
    private val targetSpecs = mutableMapOf<KonanTarget, KotlinNativeNpmPackageStageSpec>()

    fun linuxX64(action: Action<in KotlinNativeNpmPackageStageSpec>) {
        target(KonanTarget.LINUX_X64, action)
    }

    fun linuxArm64(action: Action<in KotlinNativeNpmPackageStageSpec>) {
        target(KonanTarget.LINUX_ARM64, action)
    }

    fun macosArm64(action: Action<in KotlinNativeNpmPackageStageSpec>) {
        target(KonanTarget.MACOS_ARM64, action)
    }

    fun mingwX64(action: Action<in KotlinNativeNpmPackageStageSpec>) {
        target(KonanTarget.MINGW_X64, action)
    }

    fun target(target: KonanTarget, action: Action<in KotlinNativeNpmPackageStageSpec>) {
        action.execute(forTarget(target))
    }

    internal fun forTarget(target: KonanTarget): KotlinNativeNpmPackageStageSpec =
        targetSpecs.getOrPut(target) {
            project.objects.newInstance(KotlinNativeNpmPackageStageSpec::class.java, project)
        }
}

abstract class KotlinNativeNpmPackageStageSpec @Inject constructor(private val project: Project) {
    @get:Nested
    internal val copySpecs: ListProperty<KotlinNativeNpmStageCopySpec> =
        project.objects.listProperty(KotlinNativeNpmStageCopySpec::class.java)
            .convention(emptyList())

    fun copy(source: Any) {
        addCopy(source, destinationPath = null, requireRegularFile = false)
    }

    fun copy(source: Any, path: String) {
        addCopy(source, destinationPath = path, requireRegularFile = false)
    }

    fun readme() {
        readme(project.layout.projectDirectory.file("README.md"))
    }

    fun readme(source: Any) {
        addCopy(source, destinationPath = "README.md", requireRegularFile = true)
    }

    fun license() {
        val copySpec = project.objects.newInstance(KotlinNativeNpmStageCopySpec::class.java)
        copySpec.sourceFiles.from(
            project.layout.projectDirectory.file("LICENSE"),
            project.layout.projectDirectory.file("LICENSE.txt"),
        )
        copySpec.existingFilesOnly.set(true)
        copySpec.requireRegularFile.set(true)
        copySpecs.add(copySpec)
    }

    fun license(source: Any) {
        addCopy(source, destinationPath = "LICENSE", requireRegularFile = true)
    }

    private fun addCopy(source: Any, destinationPath: String?, requireRegularFile: Boolean) {
        val copySpec = project.objects.newInstance(KotlinNativeNpmStageCopySpec::class.java)
        copySpec.sourceFiles.from(source)
        destinationPath?.let(copySpec.destinationPath::set)
        copySpec.requireRegularFile.set(requireRegularFile)
        copySpecs.add(copySpec)
    }
}

abstract class KotlinNativeNpmStageCopySpec @Inject constructor() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Optional
    @get:Input
    abstract val destinationPath: Property<String>

    @get:Input
    abstract val requireRegularFile: Property<Boolean>

    @get:Input
    abstract val existingFilesOnly: Property<Boolean>

    init {
        requireRegularFile.convention(false)
        existingFilesOnly.convention(false)
    }
}
