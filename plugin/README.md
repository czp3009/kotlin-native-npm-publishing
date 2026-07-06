# Gradle Plugin Publishing

This subproject builds and publishes the Gradle plugin:

```text
com.hiczp.kotlin-native-npm-publishing
```

This project builds with Java 21.

## Build And Validate

Run these commands from the repository root.

Build the plugin:

```shell
./gradlew -p plugin build
```

On Windows:

```shell
./gradlew.bat -p plugin build
```

Validate the plugin publication without uploading it:

```shell
./gradlew -p plugin publishPlugins --validate-only
```

On Windows:

```shell
./gradlew.bat -p plugin publishPlugins --validate-only
```

## Configure Credentials

Log in with the Gradle Plugin Portal helper:

```shell
./gradlew -p plugin login
```

On Windows:

```shell
./gradlew.bat -p plugin login
```

You can also pass credentials directly:

```shell
./gradlew -p plugin publishPlugins -Pgradle.publish.key=your-key -Pgradle.publish.secret=your-secret
```

Or use environment variables:

```shell
GRADLE_PUBLISH_KEY=your-key GRADLE_PUBLISH_SECRET=your-secret ./gradlew -p plugin publishPlugins
```

PowerShell:

```powershell
$env:GRADLE_PUBLISH_KEY = "your-key"
$env:GRADLE_PUBLISH_SECRET = "your-secret"
./gradlew.bat -p plugin publishPlugins
```

## Publish

Publish to the Gradle Plugin Portal:

```shell
./gradlew -p plugin publishPlugins
```

On Windows:

```shell
./gradlew.bat -p plugin publishPlugins
```

## Local Maven Publish

To install the plugin publication into the local Maven repository:

```shell
./gradlew -p plugin publishPluginMavenPublicationToMavenLocal
```

On Windows:

```shell
./gradlew.bat -p plugin publishPluginMavenPublicationToMavenLocal
```
