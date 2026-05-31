# kotlin native npm publishing

`kotlin-native-npm-publishing` is a Gradle plugin for publishing kotlin native executable binaries to npm as packages
that can be run with `npx`.

The plugin is intentionally small. It discovers release executable binaries from Kotlin Multiplatform native targets,
generates npm package directories, writes a small JavaScript launcher, and publishes the generated packages with
`gradle-node-plugin`.

When this plugin is applied, `com.github.node-gradle.node` is applied automatically. This project does not add its own
Node.js configuration conventions; if you need to configure Node.js, npm, registry behavior, or download settings, use
the normal gradle-node DSL directly.

## What it publishes

For each supported native target, the plugin creates one platform package containing the executable binary. It also
creates one main npm package containing the JavaScript launcher.

The main package declares the platform packages as `optionalDependencies`. At runtime, the launcher checks
`process.platform` and `process.arch`, loads the matching platform package, and executes the bundled native binary with
the original command-line arguments.

Supported host native target families:

| Kotlin target family | npm `os` | Supported npm `cpu` |
|----------------------|----------|---------------------|
| Linux                | `linux`  | `x64`, `arm64`      |
| macOS                | `darwin` | `x64`, `arm64`      |
| MinGW                | `win32`  | `x64`, `arm64`      |

The platform package suffix is derived from the Kotlin target name converted to kebab-case. For example,
`linuxX64` becomes `linux-x64`, and `macosArm64` becomes `macos-arm64`.

This plugin only handles kotlin native executable binaries. It does not publish Kotlin JS, JVM artifacts, or other
Kotlin Multiplatform outputs.

## Usage

Apply the plugin together with Kotlin Multiplatform and define native executable binaries:

```kotlin
plugins {
    kotlin("multiplatform") version "2.3.21"
    id("com.hiczp.kotlin-native-npm-publishing") version "0.0.1"
}

group = "com.example"
version = "1.0.0"

kotlin {
    linuxX64 {
        binaries {
            executable()
        }
    }

    macosArm64 {
        binaries {
            executable()
        }
    }
}

kotlinNativeNpmPublishing {
    packageName.set("@example/my-tool")
    description.set("Command-line tool published as an npm package.")
    license.set("MIT")
    repository.set("https://github.com/example/my-tool")
    keywords.addAll("kotlin", "native", "cli")

    publishArguments.addAll("--access", "public")
}
```

Publish with:

```shell
./gradlew publishKotlinNativeNpm
```

The task builds the release executable binaries, publishes the generated platform packages, and then publishes the main
package.

After publishing, users can run the command with:

```shell
npx @example/my-tool
```

## Configuration

The extension name is `kotlinNativeNpmPublishing`.

| Property           | Default                              | Description                                                              |
|--------------------|--------------------------------------|--------------------------------------------------------------------------|
| `packageName`      | `project.name`                       | Main npm package name. Scoped names such as `@scope/name` are supported. |
| `packageVersion`   | `project.version`                    | npm package version. The value must not be empty or `unspecified`.       |
| `commandName`      | Unscoped package name                | Command exposed through the npm `bin` field.                             |
| `description`      | unset                                | npm package description.                                                 |
| `license`          | unset                                | npm package license.                                                     |
| `repository`       | unset                                | Main npm package repository field.                                       |
| `homepage`         | unset                                | Main npm package homepage field.                                         |
| `keywords`         | empty                                | Main npm package keywords.                                               |
| `publishArguments` | empty                                | Extra arguments passed to each `npm publish` invocation.                 |
| `outputDirectory`  | `build/kotlin-native-npm-publishing` | Directory used for generated npm packages.                               |

Platform package names are derived from `packageName` and the Kotlin target name converted to kebab-case:

```text
my-tool -> my-tool-linux-x64
@example/my-tool -> @example/my-tool-linux-x64
```

## Node.js configuration

Because `gradle-node-plugin` is applied automatically, you can configure it in the same build if needed:

```kotlin
node {
    version.set("22.11.0")
    download.set(true)
}
```

If you do not configure `node { ... }`, gradle-node defaults are used.

## npm publishing

This plugin delegates publishing to `npm publish`. Authentication, registry selection, `.npmrc`, environment variables,
and npm tokens are handled by npm and gradle-node.

For public scoped packages, pass npm's `--access public` argument:

```kotlin
kotlinNativeNpmPublishing {
    publishArguments.addAll("--access", "public")
}
```
