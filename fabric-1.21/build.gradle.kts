plugins {
    id("net.fabricmc.fabric-loom-remap")
    id("com.gradleup.shadow")
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.1")
    mappings("net.fabricmc:yarn:1.21.1+build.3:v2")
    modImplementation("net.fabricmc:fabric-loader:0.16.5")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.103.0+1.21.1")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.12.1+kotlin.2.0.20")

    implementation(project(":core"))
    shadow(project(":core"))

    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveClassifier.set("dev")
}

tasks.shadowJar {
    archiveClassifier.set("dev-shadow")
    configurations = listOf(project.configurations.getByName("shadow"))

    exclude("natives/win*/**")
    exclude("natives/darwin*/**")
    exclude("natives/mac*/**")
    exclude("org/slf4j/**")
    exclude("META-INF/services/org.slf4j.*")
    exclude("org/apache/commons/lang3/**")
    exclude("org/apache/commons/io/**")
    exclude("org/apache/commons/codec/**")
    exclude("kotlin/**")
    exclude("kotlinx/**")
    exclude("_COROUTINE/**")
}

tasks.remapJar {
    inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })
    archiveBaseName.set("peyajCustomDisc-Fabric-1.21")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    dependsOn(tasks.shadowJar)
}

tasks.build {
    dependsOn(tasks.remapJar)
}
