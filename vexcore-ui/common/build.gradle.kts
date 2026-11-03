plugins {
    `java-library`
}

group = "dev.vexsoft.vexui"

val generatedMetrics = project(":vexcore-ui:pack").layout.buildDirectory.dir("generated/metrics")
sourceSets.main {
    resources.srcDir(generatedMetrics)
}
tasks.processResources {
    dependsOn(":vexcore-ui:pack:generateVexUIPack")
}
tasks.named("sourcesJar") {
    dependsOn(":vexcore-ui:pack:generateVexUIPack")
}

dependencies {
    api(project(":vexcore-ui:api"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.84-stable")
    testImplementation(project(":vexcore-common"))
    testImplementation("io.papermc.paper:paper-api:26.2.build.84-stable")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    testImplementation("org.spongepowered:configurate-yaml:4.2.0")
    testImplementation("net.kyori:adventure-text-minimessage:5.2.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    systemProperty("vexui.benchmark", providers.gradleProperty("vexuiBenchmark").getOrElse("false"))
    systemProperty("vexui.benchmarkReport", layout.buildDirectory.file("reports/vexui/render-benchmark.json").get().asFile.absolutePath)
    dependsOn(":vexcore-ui:pack:prepareVexUIClient")
    systemProperty("vexui.source", rootProject.projectDir.absolutePath)
    systemProperty("vexui.pack", project(":vexcore-ui:pack").layout.buildDirectory.dir("generated/pack").get().asFile.absolutePath)
    systemProperty("vexui.client", project(":vexcore-ui:pack").layout.buildDirectory.file("minecraft/26.2-client.jar").get().asFile.absolutePath)
    systemProperty("vexui.preview", layout.buildDirectory.file("reports/vexui/debug-shop.png").get().asFile.absolutePath)
}
