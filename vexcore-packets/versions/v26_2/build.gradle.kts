plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

dependencies {
    api(project(":vexcore-packets:common"))
    paperweight.paperDevBundle("26.2.build.84-stable")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

paperweight.reobfArtifactConfiguration =
    io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

tasks.test {
    // Native bootstrap logs belong with generated test output, not beside module sources.
    val runtimeDirectory = layout.buildDirectory.dir("test-runtime")
    doFirst {
        runtimeDirectory.get().asFile.mkdirs()
        workingDir(runtimeDirectory)
    }
}
