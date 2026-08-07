plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.3.0"
}

dependencies {
    api(project(":vexcore-paper-api"))
    implementation(project(":vexcore-service-registry"))
    implementation(project(":vexcore-configuration"))
    implementation(project(":vexcore-command"))
    implementation(project(":vexcore-cache"))
    implementation(project(":vexcore-signal"))
    implementation(project(":vexcore-data"))
    implementation(project(":vexcore-localization"))
    implementation(project(":vexcore-messaging"))
    implementation(project(":vexcore-gameplay"))
    implementation(project(":vexcore-inventory"))
    implementation(project(":vexcore-dialog"))
    implementation(project(":vexcore-items:common"))
    implementation(project(":vexcore-items:versions:v26_2"))
    implementation(project(":vexcore-packets:common"))
    implementation(project(":vexcore-packets:versions:v26_2"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.84-stable")

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
}

tasks.assemble { dependsOn(tasks.shadowJar) }
