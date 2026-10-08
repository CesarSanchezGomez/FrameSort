plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = property("group") as String
version = property("version") as String

fun v(name: String): String = property(name) as String

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${v("paper_api_version")}")

    // Tests use the Paper API types (YAML, Adventure) without a running server.
    testImplementation("io.papermc.paper:paper-api:${v("paper_api_version")}")
    testImplementation(platform("org.junit:junit-bom:${v("junit_version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(v("java_version").toInt()))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.test {
    useJUnitPlatform()
}

tasks.runServer {
    minecraftVersion(v("minecraft_version"))
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") { expand(props) }
}
