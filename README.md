# kotlin native npm publishing

`kotlin-native-npm-publishing` is a Gradle plugin for publishing Kotlin/Native executable binaries as npm packages that
can be run with `npx`.

The plugin applies `com.github.node-gradle.node`, generates npm package directories, writes a JavaScript launcher, and
runs `npm publish` through `gradle-node-plugin`.

## Requirements

- Java 21

## Package Layout

The plugin publishes two kinds of npm packages:

| Package          | Content                                | Purpose                                         |
|------------------|----------------------------------------|-------------------------------------------------|
| Main package     | `package.json` and JavaScript launcher | The package users install or run with `npx`.    |
| Platform package | One Kotlin/Native executable binary    | The native binary for one Kotlin/Native target. |

The main package declares platform packages as `optionalDependencies`. It also writes a
`kotlinNativeNpmPublishing.platformPackages` map from npm platform keys such as `linux-x64` or `win32-x64` to platform
package names.

At runtime, the launcher uses `process.platform` and `process.arch`, resolves the matching optional dependency, and
executes the native binary with the original command-line arguments.

Supported native target families:

| Kotlin/Native target family | npm `os` | npm `cpu`      |
|-----------------------------|----------|----------------|
| Linux                       | `linux`  | `x64`, `arm64` |
| macOS                       | `darwin` | `x64`, `arm64` |
| MinGW                       | `win32`  | `x64`, `arm64` |

Platform package names are derived from the main package name and `KonanTarget.name`:

```text
my-tool -> my-tool-linux_x64
@example/my-tool -> @example/my-tool-linux_x64
```

Only Kotlin/Native executable binaries are published. Kotlin JS, JVM, metadata, and other KMP outputs are ignored.

## Gradle Setup

Apply Kotlin Multiplatform and this plugin, then define release executable binaries for the native targets you want to
publish:

```kotlin
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform") version "2.4.0"
    id("com.hiczp.kotlin-native-npm-publishing") version "0.0.5"
}

group = "com.example"
version = "1.0.0"

kotlin {
    linuxX64()
    macosArm64()
    mingwX64()

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.executable {
            entryPoint = "com.example.main"
        }
    }
}

kotlinNativeNpmPublishing {
    packageName.set("@example/my-tool")
    description.set("Command-line tool published as an npm package.")
    license.set("MIT")
    repository.set("https://github.com/example/my-tool")
    keywords.addAll("kotlin", "native", "cli")
    access.set("public")

    stage {
        main {
            readme()
        }
    }
}
```

## Publish Tasks

Publish the main package and platform packages buildable on the current host:

```shell
./gradlew publishKotlinNativeNpm
```

Publish only the main package:

```shell
./gradlew publishKotlinNativeNpmMainPackage
```

This task does not run native compilation. The main package metadata is calculated from all supported Kotlin/Native
executable targets declared in the KMP project, not only from targets buildable on the current host.

Publish one platform package:

```shell
./gradlew publishKotlinNativeNpmLinuxX64Package
./gradlew publishKotlinNativeNpmMacosArm64Package
./gradlew publishKotlinNativeNpmMingwX64Package
```

Platform publish tasks are generated from the configured Kotlin/Native executable targets. If a target is not
configured,
the corresponding publish task is not created.

If no supported native executable target is configured, the main package is still generated, but it has no
`optionalDependencies`.

Package generation is split into prepare and finalize tasks. The prepare task clears the package stage directory, writes
the plugin-managed files, and applies `stage.main` or `stage.platforms` copy rules. The finalize task scans the final
stage directory, writes `package.json`, and includes the staged top-level files and directories in `package.json.files`.

## Split CI Publishing

Kotlin/Native cannot always build every target on one host. If one CI platform cannot build all Kotlin Multiplatform
native outputs, publish the main package and platform packages from multiple CI jobs. For example, publish the main
package and Linux platform packages from Linux CI, then publish the macOS platform package from macOS CI.

A split CI release can look like this:

```shell
# Linux CI
./gradlew publishKotlinNativeNpm

# Windows CI
./gradlew publishKotlinNativeNpmMingwX64Package

# macOS CI
./gradlew publishKotlinNativeNpmMacosArm64Package
```

Publishing all platform packages first and the main package last avoids a window where the main package references a
platform package that is not published yet. Publishing the main package earlier is still valid once the missing platform
packages are later published with the same version.

After publishing, users run:

```shell
npx @example/my-tool
```

## Extension Properties

Configure the plugin with the `kotlinNativeNpmPublishing { ... }` block.

| Property                | Default                                    | Description                                                              |
|-------------------------|--------------------------------------------|--------------------------------------------------------------------------|
| `packageName`           | `project.name`                             | Main npm package name. Scoped names such as `@scope/name` are supported. |
| `packageVersion`        | `project.version`                          | npm package version. Must not be empty or `unspecified`.                 |
| `commandName`           | `packageName` without the `@scope/` prefix | Command exposed through the npm `bin` field.                             |
| `description`           | unset                                      | npm package description.                                                 |
| `license`               | unset                                      | npm package license.                                                     |
| `repository`            | unset                                      | Main npm package repository field.                                       |
| `homepage`              | unset                                      | Main npm package homepage field.                                         |
| `keywords`              | empty                                      | Main npm package keywords.                                               |
| `registry`              | unset                                      | Passed to npm as `--registry`.                                           |
| `access`                | unset                                      | Passed to npm as `--access`, usually `public` or `restricted`.           |
| `tag`                   | unset                                      | Passed to npm as `--tag`.                                                |
| `otp`                   | unset                                      | Passed to npm as `--otp`.                                                |
| `dryRun`                | `false`                                    | Adds `--dry-run` to each `npm publish` invocation.                       |
| `provenance`            | `false`                                    | Adds `--provenance` to each `npm publish` invocation.                    |
| `provenanceFile`        | unset                                      | Passed to npm as `--provenance-file`.                                    |
| `publishArguments`      | empty                                      | Extra arguments appended to each `npm publish` invocation.               |
| `stage.outputDirectory` | `build/kotlinNativeNpmPublishing`          | Directory used for generated npm packages.                               |

Use `publishArguments` only for npm publish options that are not modeled by the plugin.

### Gradle Properties

Frequently changing parameters can be passed in as Gradle properties and forwarded to the plugin. For example:

```kotlin
kotlinNativeNpmPublishing {
    otp.set(providers.gradleProperty("npmOtp"))
}
```

Then pass the value on the command line:

```shell
./gradlew publishKotlinNativeNpm -PnpmOtp=123456
```

## Stage DSL

Use the stage DSL for normal package file customization. A custom Gradle task is usually not needed just to copy files
into the stage directory.

```kotlin
kotlinNativeNpmPublishing {
    stage {
        outputDirectory.set(layout.buildDirectory.dir("kotlinNativeNpmPublishing"))

        main {
            readme()
            license()
            copy("CHANGELOG.md")
            copy("docs", "docs")
        }

        platforms {
            readme()
            copy("NOTICE.txt")

            linuxX64 {
                copy("resources/linux-x64", "resources")
            }
            linuxArm64 {
                copy("resources/linux-arm64", "resources")
            }
            macosArm64 {
                readme("docs/README-macos.md")
            }
            mingwX64 {
                copy("scripts/setup.ps1")
            }
            target(org.jetbrains.kotlin.konan.target.KonanTarget.MACOS_X64) {
                readme("docs/README-macos.md")
            }
        }
    }
}
```

Configure main package files with `stage.main { ... }` and platform package files with `stage.platforms { ... }`. Both
blocks support the same methods:

| Method             | Behavior                                                                                                                   |
|--------------------|----------------------------------------------------------------------------------------------------------------------------|
| `copy(file)`       | Copies one file into the package root, or recursively copies one directory into the root.                                  |
| `copy(file, path)` | Copies one file or directory to a relative path under the package root.                                                    |
| `readme(file)`     | Copies one file to `README.md`.                                                                                            |
| `readme()`         | Copies the current Gradle project's `README.md`; fails if it does not exist.                                               |
| `license(file)`    | Copies one file to `LICENSE`.                                                                                              |
| `license()`        | Copies the current Gradle project's existing `LICENSE` and `LICENSE.txt`, preserving their names; fails if neither exists. |

`readme()` and `license()`, including their overloads, are convenience methods over `copy(...)` for common npm package
files.

Inside `platforms`, use `linuxX64`, `linuxArm64`, `macosArm64`, or `mingwX64` blocks for
target-specific files. These names match KMP's default target names. Each block supports the same file methods above.
Matching uses the native target, not the host OS or a custom KMP target name. These blocks do not create KMP targets;
Rules for targets without a configured, supported release executable are ignored without an unmatched-target error.
You can share the same stage configuration across projects with different targets; unused rules do not resolve or
validate their source files.

Use `target(KonanTarget.MACOS_X64) { ... }` for targets without a named shortcut, such as deprecated KMP targets.
Import `org.jetbrains.kotlin.konan.target.KonanTarget` to use this shorter form. The generic `target(...)` block accepts
any `KonanTarget`, including targets for which this plugin does not generate packages; their rules are simply unused.
Named shortcuts and generic blocks for the same target share their rules.

Common platform rules always run before target-specific rules, regardless of where the blocks appear in the DSL.
Target-specific files can therefore overwrite common files. Repeated blocks for the same target append their rules
in declaration order. Main package files are unaffected.

The plugin writes its launcher or native executable before applying stage copy rules. If a stage copy overwrites those
files, the user-provided files win.

For unusual cases where another Gradle task must generate files directly into the stage directory, make that task run
after prepare and before finalize:

```kotlin
val addExtraMainFiles by tasks.registering(Copy::class) {
    dependsOn(tasks.named("prepareKotlinNativeNpmMainPackage"))
    from("extra")
    into(kotlinNativeNpmPublishing.stage.outputDirectory.dir("main"))
}

tasks.named("finalizeKotlinNativeNpmMainPackage") {
    dependsOn(addExtraMainFiles)
}
```

## Example

See [`example`](example/README.md) for a runnable demo.
