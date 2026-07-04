package com.hiczp.kotlin.native.npm.publishing

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
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

    val outputDirectory: DirectoryProperty = project.objects.directoryProperty()
        .convention(project.layout.buildDirectory.dir("kotlin-native-npm-publishing"))
}
