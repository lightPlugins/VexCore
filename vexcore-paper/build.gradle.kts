plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.3.0"
}

dependencies {
    api(project(":vexcore-paper-api"))
    implementation(project(":vexcore-common"))
    implementation(project(":vexcore-services"))
    implementation(project(":vexcore-screen-ui:versions:v26_2"))
    implementation(project(":vexcore-items:versions:v26_2"))
    implementation(project(":vexcore-nms:versions:v26_2"))
    implementation(project(":vexcore-packets:versions:v26_2"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.84-stable")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    testImplementation("io.papermc.paper:paper-api:26.2.build.84-stable")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("VexCore")
    archiveClassifier.set("")
    mergeServiceFiles()
    relocate("org.spongepowered.configurate", "dev.vexsoft.core.libs.configurate")
    relocate("org.yaml.snakeyaml", "dev.vexsoft.core.libs.snakeyaml")
    relocate("io.leangen.geantyref", "dev.vexsoft.core.libs.geantyref")
    relocate("com.ezylang.evalex", "dev.vexsoft.core.libs.evalex")
}

tasks.assemble { dependsOn(tasks.shadowJar) }

val screenUiPackDirectory = layout.buildDirectory.dir("generated/screen-ui-pack")
val screenUiPackGenerator = rootProject.layout.projectDirectory.file("resource-pack-tools/GenerateScreenUiPack.java")
val screenUiFontDirectory = rootProject.layout.projectDirectory.dir("resource-pack-tools/fonts")
val screenUiMetricsDirectory = layout.buildDirectory.dir("generated/screen-ui-metrics")
val screenUiVersionPack = rootProject.layout.projectDirectory.dir("vexcore-screen-ui/versions/v26_2/pack")
val generateScreenUiPack = tasks.register<Exec>("generateScreenUiPack") {
    group = "build"
    description = "Generates the standalone VexCore bossbar UI resource pack."
    inputs.file(screenUiPackGenerator)
    inputs.dir(screenUiFontDirectory)
    inputs.dir(screenUiVersionPack)
    outputs.dir(screenUiPackDirectory)
    outputs.dir(screenUiMetricsDirectory)
    val launcher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }
    doFirst {
        // Only these fixed generated-output directories are replaced; removed registrations must not survive.
        project.delete(screenUiPackDirectory.get().asFile, screenUiMetricsDirectory.get().asFile)
        commandLine(launcher.get().executablePath.asFile.absolutePath, "-Djava.awt.headless=true",
            screenUiPackGenerator.asFile.absolutePath, screenUiPackDirectory.get().asFile.absolutePath,
            screenUiFontDirectory.asFile.absolutePath, screenUiMetricsDirectory.get().asFile.absolutePath,
            screenUiVersionPack.asFile.absolutePath)
    }
}
val screenUiResourcePack = tasks.register<Zip>("screenUiResourcePack") {
    group = "build"
    dependsOn(generateScreenUiPack)
    from(screenUiPackDirectory)
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    archiveBaseName.set("VexCore-ResourcePack")
    archiveVersion.set(project.version.toString())
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.assemble { dependsOn(screenUiResourcePack) }
