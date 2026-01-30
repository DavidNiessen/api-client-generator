import org.gradle.internal.extensions.stdlib.capitalized
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

private val githubUser: String? = System.getenv("GITHUB_ACTOR")
private val githubToken: String? = System.getenv("GITHUB_TOKEN")
private val githubRepo: String = "api-client-generator"

private val apiSpecsDir = file("$rootDir/api/")
private val generatedClientsDir = file("$rootDir/generated/")

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


plugins {
    kotlin("jvm") version "2.2.21"
    id("org.openapi.generator") version "7.19.0"
    id("maven-publish")
    application
}

val generateTasks = apiClients.map {
    tasks.register<GenerateTask>("generate${it.name.capitalized()}ClientName") {
        this.inputSpec = it.apiSpec.absolutePath
        this.configFile = it.config.absolutePath
        this.outputDir = it.generatedFolder().absolutePath
    }
}

group = "dev.niessen"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(24)
}

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
                "cd ${it.absolutePath} && sh gradlew build"
            }
        )
    }
}

apiClients.forEach { client ->
    val jarFile = client.findGeneratedJar()

    if (jarFile != null) {
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
                    url = uri("https://maven.pkg.github.com/DavidNiessen/$githubRepo")
                    credentials {
                        username = githubUser
                        password = githubToken
                    }
                }
            }
        }

        tasks.register("publish${client.name.capitalized()}") {
            group = "publishing"
            dependsOn(buildGeneratedClients)
            doLast {
                println("Publishing ${client.name} to GitHub Packages")
            }
        }
    } else {
        println("⚠️ No JAR found for ${client.name}, skipping publishing")
    }
}

tasks.register("publishAllGeneratedClients") {
    group = "publishing"
    dependsOn(apiClients.map { "publish${it.name.capitalized()}" })
}

tasks.named("compileKotlin") {
    dependsOn(generateTasks, buildGeneratedClients)
}

tasks.test {
    useJUnitPlatform()
}

