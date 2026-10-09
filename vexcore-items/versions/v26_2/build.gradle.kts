plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "dev.vexsoft.items.versions"

dependencies {
    api(project(":vexcore-items:common"))
    paperweight.paperDevBundle("26.2.build.84-stable")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

paperweight.reobfArtifactConfiguration =
    io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

tasks.test {
    val runtimeDirectory = layout.buildDirectory.dir("test-runtime")
    doFirst {
        runtimeDirectory.get().asFile.mkdirs()
        workingDir(runtimeDirectory)
    }
}
