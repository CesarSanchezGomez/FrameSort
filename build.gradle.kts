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
    // Each third-party repository serves only its own groups, so a slow one cannot break other lookups.
    maven("https://maven.enginehub.org/repo/") {
        name = "enginehub"
        content {
            includeGroupAndSubgroups("com.sk89q")
            includeGroupAndSubgroups("org.enginehub")
        }
    }
    maven("https://repo.momirealms.net/releases/") {
        name = "momirealms"
        content { includeGroup("net.momirealms") }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${v("paper_api_version")}")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:${v("worldguard_version")}")
    compileOnly("net.momirealms:craft-engine-core:${v("craftengine_version")}")
    compileOnly("net.momirealms:craft-engine-bukkit:${v("craftengine_version")}")
    // Downloaded on first start by FrameSortLoader, which names the same version.
    compileOnly("com.zaxxer:HikariCP:${v("hikaricp_version")}")

    // Tests use the Paper API types (YAML, Adventure) without a running server.
    testImplementation("io.papermc.paper:paper-api:${v("paper_api_version")}")
    testImplementation(platform("org.junit:junit-bom:${v("junit_version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Database tests run against real SQLite with the driver version the server ships.
    testImplementation("com.zaxxer:HikariCP:${v("hikaricp_version")}")
    testRuntimeOnly("org.xerial:sqlite-jdbc:${v("sqlite_jdbc_version")}")
    // The SQL that differs by dialect also runs against real MySQL and MariaDB: ./gradlew mysqlTest, with Docker.
    testImplementation("org.testcontainers:testcontainers-mysql:${v("testcontainers_version")}")
    testImplementation("org.testcontainers:testcontainers-mariadb:${v("testcontainers_version")}")
    testRuntimeOnly("com.mysql:mysql-connector-j:${v("mysql_connector_version")}")
    testRuntimeOnly("org.mariadb.jdbc:mariadb-java-client:${v("mariadb_client_version")}")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(v("java_version").toInt()))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
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
