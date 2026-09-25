plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":core"))

    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9")
    compileOnly("org.geysermc.geyser:api:2.4.2-SNAPSHOT")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.0")

    implementation("org.bstats:bstats-bukkit:3.0.2")
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, "seconds")
    }
}

tasks.shadowJar {
    archiveBaseName.set("peyajCustomDisc-Paper")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

    exclude("natives/win*/**")
    exclude("natives/darwin*/**")
    exclude("natives/mac*/**")
    exclude("org/slf4j/**")
    exclude("META-INF/services/org.slf4j.*")
    exclude("org/apache/commons/lang3/**")
    exclude("org/apache/commons/io/**")
    exclude("org/apache/commons/codec/**")
    relocate("org.bstats", "com.peyaj.jukeboxweb.bstats")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
