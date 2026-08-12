plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.3.0"
}

dependencies {
    implementation(project(":vexcore-velocity-api"))
    implementation(project(":vexcore-common"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.2")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.postgresql:postgresql:42.7.8")

    compileOnly("com.velocitypowered:velocity-api:4.1.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:4.1.0-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.shadowJar {
    archiveBaseName.set("VexCore-Velocity")
    archiveClassifier.set("")
    mergeServiceFiles()
    relocate("com.fasterxml.jackson", "dev.vexsoft.core.libs.jackson")
    relocate("com.github.benmanes.caffeine", "dev.vexsoft.core.libs.caffeine")
    relocate("org.spongepowered.configurate", "dev.vexsoft.core.libs.configurate")
    relocate("org.yaml.snakeyaml", "dev.vexsoft.core.libs.snakeyaml")
    relocate("io.leangen.geantyref", "dev.vexsoft.core.libs.geantyref")
}

tasks.assemble { dependsOn(tasks.shadowJar) }
