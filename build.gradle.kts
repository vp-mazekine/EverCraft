plugins {
    kotlin("jvm") version "1.6.0"
    java
}

group = "com.mazekine"
version = "0.2.1"

val targetJavaVersion = 16
val ktorVersion = "1.6.7" //"1.6.5"
val logbackVersion = "1.2.8" //"1.2.7"
val tonClientVersion = "0.0.42"
val jacksonVersion = "2.11.4"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://papermc.io/repo/repository/maven-public/")
    }
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    implementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.6.0")

    //  Minecraft
    implementation("io.papermc.paper:paper-api:1.17.1-R0.1-SNAPSHOT")

    //  JSON
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    //  Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    //  REST
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-gson:$ktorVersion")

    //  Security
    implementation("org.bouncycastle:bcprov-jdk15on:1.69")

    //  EVER
    implementation("ee.nx-01.tonclient:ton-client-kotlin:$tonClientVersion")

    //  TON SDK dependencies
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("io.github.microutils:kotlin-logging:1.7.9")
    implementation("org.scijava:native-lib-loader:2.3.4")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")

    //  Bukkit dependencies
    implementation("org.apache.logging.log4j:log4j-core:2.16.0")

    //  bStats
    implementation("org.bstats:bstats-bukkit:2.2.1")
}

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if(JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.withType(JavaCompile::class).configureEach {
    if(targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.withType(ProcessResources::class) {
    val props: LinkedHashMap<String, String> = linkedMapOf("version" to version.toString())
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        this.expand(props)
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set(project.name + ".jar")
    exclude("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF")
    from(configurations.compileClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.test {
    useJUnitPlatform()
}