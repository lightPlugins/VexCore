plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.3.0"
}

dependencies {
    api(project(":vexcore-api"))
    implementation(project(":vexcore-service-registry"))
    implementation(project(":vexcore-configuration"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.84-stable")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("VexCore")
    archiveClassifier.set("")
    relocate("org.spongepowered.configurate", "dev.vexsoft.core.libs.configurate")
    relocate("org.yaml.snakeyaml", "dev.vexsoft.core.libs.snakeyaml")
    relocate("io.leangen.geantyref", "dev.vexsoft.core.libs.geantyref")
}

tasks.jar { enabled = false }
tasks.assemble { dependsOn(tasks.shadowJar) }
