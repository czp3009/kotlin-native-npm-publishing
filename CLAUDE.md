# Repository Guidelines

## Project Structure & Module Organization

This repository contains a Gradle plugin for publishing Kotlin/Native executables as npm packages.

- `plugin/` contains the Gradle plugin implementation.
- `plugin/src/main/kotlin/com/hiczp/kotlin/native/npm/publishing/` contains the plugin entry point, extension, and
  package generation tasks.
- `plugin/src/main/resources/.../wrapper.js` is the JavaScript launcher included in the main npm package.
- `example/` is a Kotlin Multiplatform demo project that applies the plugin and publishes a runnable npm package.
- `gradle/libs.versions.toml` owns dependency and plugin versions.
- `gradle.properties` owns the project version.

Generated output lives under `build/` or `example/build/` and should not be committed.

## Build, Test, and Development Commands

- `./gradlew.bat -p plugin build` builds and checks the plugin on Windows.
- `./gradlew -p plugin build` is the equivalent command on Unix-like systems.
- `./gradlew.bat :example:tasks --all` lists generated example tasks, including platform publish tasks.
- `./gradlew.bat :example:publishKotlinNativeNpm -PnpmPackageName=@scope/name -PnpmDryRun=true` validates npm package
  generation without publishing.
- `./gradlew.bat :example:publishKotlinNativeNpm -PnpmPackageName=@scope/name -PnpmRegistry=http://127.0.0.1:4873`
  publishes the demo to a local registry.

Use `--configuration-cache --configuration-cache-problems=fail` when changing task registration or task inputs.

## Coding Style & Naming Conventions

Use Kotlin DSL and idiomatic Kotlin. Keep logic direct and small; avoid extra models, enums, or helper functions unless
they remove real complexity. Prefer Kotlin Multiplatform and Kotlin/Native APIs over hand-written platform tables.

Use 4-space indentation in Kotlin and Gradle files. Keep package names aligned with Kotlin package style, except where
npm package naming requires another format.

## Testing Guidelines

There is no separate test suite yet. Validate changes through Gradle builds and the example project. For publishing
behavior, test with a local npm registry such as Verdaccio and inspect both main and platform package `package.json`
files.

Confirm that `npx --registry <registry> <package> arg1 arg2` runs the published example.
