plugins {
    kotlin("jvm") version "2.4.0"
    application
}

group = "org.jetbrains.kotlinx"
version = "0.0.1"

repositories {
    mavenCentral()

}

application {
    mainClass = "MainKt"
}

kotlin {
    jvmToolchain(21)
}

val appArgs: String? by project
tasks.named<JavaExec>("run") {
    appArgs?.let {
        for (argLine in project.file(it).readLines()) {
            val args = argLine.split(' ', limit = 2).filter(String::isNotBlank)
            if (args.isNotEmpty()) args(args)
        }
    }
}
