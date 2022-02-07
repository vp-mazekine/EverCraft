plugins {
    kotlin("jvm") //version "1.6.0" apply false
}

allprojects {
    group = "com.mazekine"
    version = "0.2.2-RC5"

    repositories {
        mavenCentral()
    }

}

dependencies {
    subprojects.forEach {
        add("archives", it)
    }
}

configure(listOf(":shared")) {
    val compileKotlinTask = tasks.getByName("compileKotlin") as org.jetbrains.kotlin.gradle.tasks.KotlinCompile
    afterEvaluate {
        pluginManager.withPlugin("java-library") {
            configurations {
                "apiElements" {
                    outgoing
                        .variants
                        .getByName("classes")
                        .artifact(mapOf(
                            "file" to compileKotlinTask.destinationDirectory,
                            "type" to "java-classes-directory",
                            "builtBy" to compileKotlinTask
                        ))
                }
            }
        }
    }
}