group = "com.hiczp"
version = providers.gradleProperty("projectVersion").get()

subprojects {
    group = rootProject.group
    version = rootProject.version
}
