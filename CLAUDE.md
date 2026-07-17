# Repository Guide for Agents

This repository contains the Gradle plugin `com.hiczp.kotlin-native-npm-publishing`. It turns release executables from a
Kotlin Multiplatform project into a main, `npx`-runnable npm package plus one optional npm package per native target.

## Start Here

The repository is small; use this map instead of rediscovering responsibilities:

- `plugin/src/main/kotlin/com/hiczp/kotlin/native/npm/publishing/KotlinNativeNpmPublishingPlugin.kt` applies the Node
  plugin, creates the extension, discovers Kotlin/Native release executables, and registers/configures all Gradle tasks.
- `plugin/src/main/kotlin/com/hiczp/kotlin/native/npm/publishing/KotlinNativeNpmPublishingExtension.kt` defines the
  public DSL, conventions, and stage copy specifications.
- `plugin/src/main/kotlin/com/hiczp/kotlin/native/npm/publishing/task/GenerateKotlinNativeNpmPackageTasks.kt` implements
  package staging, validation, package naming, and `package.json` generation.
- `plugin/src/main/resources/com/hiczp/kotlin/native/npm/publishing/wrapper.js` is the launcher shipped in the main npm
  package. It selects and executes the installed platform package at runtime.
- `example/build.gradle.kts` is the integration fixture and the most complete in-repository usage example.
- `README.md` documents public plugin behavior and the DSL. `example/README.md` covers end-to-end npm/Verdaccio usage,
  while `plugin/README.md` covers publishing the Gradle plugin itself.

The root build includes `plugin/` as a composite build and includes `example/` as its only subproject. `plugin/` can
also be built as a standalone Gradle project. Dependency/plugin versions live in `gradle/libs.versions.toml`; the
project/plugin version lives in `gradle.properties`.

## Implementation Model and Invariants

The plugin always applies `com.github.node-gradle.node` and registers the main-package tasks. Native target discovery is
deferred with `pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform")`, so applying the two plugins in either
order works.

Only `RELEASE` binaries of type `Executable` are considered. Supported targets are Linux, macOS, and MinGW with `x64`
or `arm64` architecture. Other KMP outputs and native families are ignored. There must be at most one release executable
per `KonanTarget`; a second one fails configuration deliberately.

Platform tasks are registered for every configured, supported release executable, even if that target cannot be built on
the current host. Only host-enabled platform tasks are attached to the aggregate generate/publish tasks. In contrast,
main-package metadata includes every configured supported target on every host; this is how split CI builds produce the
same `optionalDependencies` list.

The task graph is:

```text
prepare main -> finalize main -> generate main -> generateKotlinNativeNpmPackages
link release executable -> prepare platform -> finalize platform -> generate platform -> aggregate generate (host only)

finalize main -> publish main -> publishKotlinNativeNpm
finalize platform -> publish platform -> aggregate publish (host only)
```

When platform and main publish tasks are in the same invocation, `mustRunAfter` publishes platform packages before the
main package. Publishing the main package alone does not compile native binaries.

Default stage output is:

```text
build/kotlinNativeNpmPublishing/
  main/
    bin/<commandName>.js
    package.json
  platforms/<konanTarget.name>/
    bin/<native executable>
    package.json
```

Every prepare task deletes its package directory, recreates plugin-managed files, and then applies the relevant
`stage.main` or `stage.platforms` copy rules. Consequently, stage rules may intentionally overwrite managed files.
Finalize tasks scan the final directory and put its top-level entries, except `package.json`, in `package.json.files`.
These staging tasks always run and intentionally have Gradle build caching disabled because their output is temporary
publication state.

The main package contains the JavaScript launcher, a `bin` entry, the platform-name map, and matching
`optionalDependencies`. A platform package contains one native executable and npm `os`/`cpu` restrictions. Platform
package names append `-<konanTarget.name>` while preserving an npm scope, for example
`@scope/tool -> @scope/tool-linux_x64`.

At runtime, `wrapper.js` maps Node's `<process.platform>-<process.arch>` to a platform package, resolves its
`package.json`, reads `kotlinNativeNpmPublishing.binary`, and calls it synchronously with the original arguments and
inherited stdio. Keep generated metadata and wrapper behavior in sync when changing either side.

## Public DSL and Change Boundaries

The extension is named `kotlinNativeNpmPublishing`. Its main concerns are:

- npm identity/metadata: `packageName`, `packageVersion`, `commandName`, description, license, repository, homepage, and
  keywords;
- npm publish flags: registry, access, tag, OTP, dry run, provenance, provenance file, and additional arguments;
- package customization: `stage.outputDirectory`, `stage.main`, and `stage.platforms`.

Stage copy destinations must be relative paths that stay inside the package directory. Each configured source must
resolve to exactly one existing file or directory. `readme()` and `license()` require regular files. Preserve these
validation and configuration-cache-friendly Provider/task-input semantics when extending the DSL.

Keep responsibilities in their current files: task registration and wiring in the plugin class, configuration only in
the extension, filesystem/package JSON behavior in the task file, and runtime package selection in `wrapper.js`. Avoid
adding platform lookup tables when Kotlin/Native's `KonanTarget`, `Family`, `architecture`, or `HostManager` already
provide the information.

## Build and Validation

Java 21 is required for the plugin build. From the repository root on Windows:

```powershell
./gradlew.bat -p plugin build
./gradlew.bat :example:tasks --all
./gradlew.bat :example:publishKotlinNativeNpm `
  -PnpmPackageName=@scope/name -PnpmDryRun=true
```

Use `./gradlew` instead of `./gradlew.bat` on Unix-like systems. Add
`--configuration-cache --configuration-cache-problems=fail` when changing DSL properties, task inputs, registration, or
dependencies. The repository has no separate test source set; validate with the plugin build and the example.

For publication changes, inspect both generated `package.json` variants under
`example/build/kotlinNativeNpmPublishing/`. Use a local registry such as Verdaccio for a full check, and confirm:

```text
npx --registry <registry> <package> arg1 arg2
```

Generated output under any `build/` directory must not be committed. Preserve unrelated working-tree changes.

## Style

Use idiomatic Kotlin and Kotlin DSL with 4-space indentation. Keep logic direct and small; introduce helpers or models
only when they remove real complexity. Prefer Kotlin Multiplatform/Kotlin/Native APIs and lazy Gradle Providers over
eager values or hand-maintained platform data.
