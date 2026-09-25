dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.javalin:javalin:6.1.3")
    compileOnly("org.slf4j:slf4j-simple:2.0.7")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("org.apache.commons:commons-compress:1.26.0") {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "commons-io", module = "commons-io")
    }
    implementation("org.tukaani:xz:1.9")
}
