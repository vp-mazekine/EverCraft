plugins {
    kotlin("jvm") // version "1.6.0" apply false
    id("org.jlleitschuh.gradle.ktlint") version "10.3.0"
}

allprojects {
    group = "com.mazekine"
    version = "0.2.4-alpha"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        verbose.set(true)
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
                        .artifact(
                            mapOf(
                                "file" to compileKotlinTask.destinationDirectory,
                                "type" to "java-classes-directory",
                                "builtBy" to compileKotlinTask
                            )
                        )
                }
            }
        }
    }
}
