# kotlin native npm publishing example

This demo publishes a Kotlin/Native executable as an npm package and runs it with `npx`.

All publish commands require an npm package name:

```shell
-PnpmPackageName=@czp3009/kotlin-native-npm-publishing-example
```

## Publish Tasks

Dry-run publish of the main package and platform packages buildable on the current host:

```shell
./gradlew :example:publishKotlinNativeNpm -PnpmPackageName=@czp3009/kotlin-native-npm-publishing-example
```

This demo uses `npm --dry-run` by default when neither `npmRegistry` nor `npmAccess` is provided.

Publish only the main package:

```shell
./gradlew :example:publishKotlinNativeNpmMainPackage -PnpmPackageName=@czp3009/kotlin-native-npm-publishing-example
```

Publish one platform package:

```shell
./gradlew :example:publishKotlinNativeNpmMingwX64Package -PnpmPackageName=@czp3009/kotlin-native-npm-publishing-example
```

## Local Registry

Start a local npm registry:

```shell
npx verdaccio --listen 127.0.0.1:4873
```

If the registry requires authentication, log in first:

```shell
npm login --registry http://127.0.0.1:4873
```

Publish to the local registry:

```shell
./gradlew :example:publishKotlinNativeNpm -PnpmPackageName=@czp3009/kotlin-native-npm-publishing-example -PnpmRegistry=http://127.0.0.1:4873
```

Inspect the published main package metadata:

```shell
npm view --registry http://127.0.0.1:4873 @czp3009/kotlin-native-npm-publishing-example --json
```

Install from the local registry into a temporary directory:

```shell
npm install --registry http://127.0.0.1:4873 --prefix build/npm-install-check @czp3009/kotlin-native-npm-publishing-example
```

Run with `npx`:

```shell
npx --registry http://127.0.0.1:4873 @czp3009/kotlin-native-npm-publishing-example arg1 arg2
```

Expected output on Windows:

```text
kotlin-native-npm-publishing example
platform: Windows
args: arg1, arg2
```

## npm Registry

Log in to npm:

```shell
npm login
```

Publish the public scoped package:

```shell
./gradlew :example:publishKotlinNativeNpm -PnpmPackageName=@czp3009/kotlin-native-npm-publishing-example -PnpmAccess=public
```

Run after publishing:

```shell
npx @czp3009/kotlin-native-npm-publishing-example arg1 arg2
```

## Publish Options

These Gradle properties are passed through to `npm publish`:

| Gradle property     | npm option          | Meaning                                                                             |
|---------------------|---------------------|-------------------------------------------------------------------------------------|
| `npmRegistry`       | `--registry`        | Publish to the given npm registry URL instead of the npm default registry.          |
| `npmAccess`         | `--access`          | Set package access for scoped packages, usually `public` or `restricted`.           |
| `npmTag`            | `--tag`             | Set the dist-tag assigned to the published version, such as `latest` or `beta`.     |
| `npmOtp`            | `--otp`             | Pass a one-time password when the npm account requires two-factor authentication.   |
| `npmDryRun`         | `--dry-run`         | Ask npm to pack and validate the package without publishing it.                     |
| `npmProvenance`     | `--provenance`      | Ask npm to publish with provenance when the registry and CI environment support it. |
| `npmProvenanceFile` | `--provenance-file` | Pass the provenance bundle file used by npm publish.                                |

Example using all options:

```shell
./gradlew :example:publishKotlinNativeNpm -PnpmPackageName=@czp3009/kotlin-native-npm-publishing-example -PnpmRegistry=https://registry.npmjs.org/ -PnpmAccess=public -PnpmTag=beta -PnpmOtp=123456 -PnpmDryRun=true -PnpmProvenance=true -PnpmProvenanceFile=provenance.json
```
