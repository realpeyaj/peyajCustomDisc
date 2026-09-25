plugins {
    id("net.fabricmc.fabric-loom")
    id("com.gradleup.shadow")
}

dependencies {
    minecraft("com.mojang:minecraft:26.3")
    implementation("net.fabricmc:fabric-loader:0.19.5")
    implementation("net.fabricmc.fabric-api:fabric-api:0.161.0+26.3")
    implementation("net.fabricmc:fabric-language-kotlin:1.14.1+kotlin.2.4.20")

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

tasks.shadowJar {
    archiveBaseName.set("peyajCustomDisc-Fabric-26")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

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

tasks.build {
    dependsOn(tasks.shadowJar)
}
