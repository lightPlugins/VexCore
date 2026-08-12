plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.3.0"
}

dependencies {
    api(project(":vexcore-paper-api"))
    implementation(project(":vexcore-common"))
    implementation(project(":vexcore-services"))
    implementation(project(":vexcore-items:versions:v26_2"))
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
