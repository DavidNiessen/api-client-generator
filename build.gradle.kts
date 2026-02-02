import org.gradle.internal.extensions.stdlib.capitalized
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

private val githubRepo: String = "api-client-generator"
private val packagesUrl = "https://maven.pkg.github.com/DavidNiessen/$githubRepo"

private val githubUser: String? = System.getenv("GITHUB_ACTOR")
private val githubToken: String? = System.getenv("GITHUB_TOKEN")

private val apiSpecsDir = file("$rootDir/api/")
private val generatedClientsDir = file("$rootDir/generated/")

group = "dev.niessen"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

plugins {
    kotlin("jvm") version "2.2.21"
    id("org.openapi.generator") version "7.9.0"
    id("maven-publish")
    application
}

//
// API CLIENT GENERATION
//
data class ApiClient(
    val name: String,
    val apiSpec: File,
    val config: File,
) {

    fun generatedFolder() = file("${generatedClientsDir.absolutePath}/$name")

    fun findGeneratedJar() = file("${generatedFolder().absolutePath}/build/libs")
        .listFiles()
        ?.find { it.name.endsWith(".jar") }

}

val apiClients = apiSpecsDir.listFiles()
    ?.filter { it.isDirectory }
    ?.mapNotNull { dir ->
        val apiSpec = dir.listFiles()?.find { it.nameWithoutExtension == "openapi" }
        val config = dir.listFiles()?.find { it.name == "config.json" }

        if (apiSpec != null && config != null) ApiClient(
            name = dir.name,
            apiSpec = apiSpec,
            config = config
        ) else null
    } ?: emptyList()

val generateTasks = apiClients.map {
    tasks.register<GenerateTask>("generate${it.name.capitalized()}ClientName") {
        this.inputSpec = it.apiSpec.absolutePath
        this.configFile = it.config.absolutePath
        this.outputDir = it.generatedFolder().absolutePath
        this.importMappings = mapOf(
            "BigDecimal" to "java.math.BigDecimal"
        )
    }
}

//
// API CLIENT BUILDING
//
val buildGeneratedClients = tasks.register<Exec>("buildGeneratedClients") {
    mustRunAfter(generateTasks)
    group = "build"
    description = "Build all generated api clients"

    doFirst {
        val folders = apiClients.map { it.generatedFolder() }
            .filter {
                it.isDirectory
                        && it.listFiles { file: File ->
                    file.name == "gradlew"
                }.isNotEmpty()
            }

        if (folders.isEmpty()) {
            println("No generated clients found")
        }

        commandLine(
            "sh", "-c",
            folders.joinToString(" && ") {
                "set -x && cd ${it.absolutePath} && sh ./gradlew build -x test -Pjakarta_annotation_version=\"3.0.0\""
            }
        )
    }
}

//
// API CLIENT PUBLISHING
//
apiClients.forEach { client ->
    val jarFile = client.findGeneratedJar()

    if (jarFile == null) {
        println("no jar found for ${client.name}")
        return@forEach
    }

    publishing {
        publications {
            create<MavenPublication>(client.name) {
                groupId = "dev.niessen"
                artifactId = client.name
                version = System.currentTimeMillis().toString()

                artifact(jarFile)
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri(packagesUrl)
                credentials {
                    username = githubUser
                    password = githubToken
                }
            }
        }
    }
}

tasks.register("publishAllGeneratedClients") {
    group = "publishing"
    dependsOn(apiClients.map { "publish${it.name.capitalized()}PublicationToGitHubPackagesRepository" })
}

tasks.named("compileKotlin") {
    dependsOn(generateTasks, buildGeneratedClients)
}

tasks.test {
    useJUnitPlatform()
}

