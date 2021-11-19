plugins {
    kotlin("jvm") version "1.6.0"
    java
}

group = "com.mazekine"
version = "0.0.1"

val targetJavaVersion = 16

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
    implementation("io.papermc.paper:paper-api:1.17.1-R0.1-SNAPSHOT")
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
    from(configurations.compileClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}