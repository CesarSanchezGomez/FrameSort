import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    alias(libs.plugins.run.paper)
    alias(libs.plugins.errorprone)
}

group = property("group") as String
version = property("version") as String

fun v(name: String): String = property(name) as String

dependencies {
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)
    compileOnly(libs.paper.api)
    compileOnly(libs.worldguard.bukkit)
    compileOnly(libs.craftengine.core)
    compileOnly(libs.craftengine.bukkit)
    // Downloaded on first start by FrameSortLoader, which names the same version.
    compileOnly(libs.hikaricp)

    // Tests use the Paper API types (YAML, Adventure) without a running server.
    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // Database tests run against real SQLite with the driver version the server ships.
    testImplementation(libs.hikaricp)
    testRuntimeOnly(libs.sqlite.jdbc)
    // The SQL that differs by dialect also runs against real MySQL and MariaDB: ./gradlew mysqlTest, with Docker.
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.testcontainers.mariadb)
    testRuntimeOnly(libs.mysql.connector.j)
    testRuntimeOnly(libs.mariadb.java.client)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(v("java_version").toInt()))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
    // A @Nullable dereferenced or returned where a value is required stops the build. Error Prone's warnings stay
    // off: the build allows no warnings, and its errors are what flags a bug.
    options.errorprone {
        disableAllWarnings = true
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:JSpecifyMode", "true")
        option("NullAway:OnlyNullMarked", "true")
    }
}

// A null in a test fails that test when it runs, so the test run is already the check.
tasks.compileTestJava {
    options.errorprone.enabled = false
}

tasks.test {
    useJUnitPlatform { excludeTags("mysql") }
    // sqlite-jdbc loads a native library; Java 24+ warns unless native access is granted.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<Test>("mysqlTest") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("mysql") }
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
