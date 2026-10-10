plugins {
    kotlin("jvm") version "2.0.20"
    application
}

repositories { mavenCentral() }

kotlin {
    sourceSets.main {
        kotlin.srcDir("../src/model")
    }
}

application {
    mainClass.set("TerminalGameKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
