plugins {
    `java-library`
}

sourceSets.main {
    resources.srcDir(project(":vexcore-paper").layout.buildDirectory.dir("generated/screen-ui-metrics"))
}
tasks.processResources { dependsOn(":vexcore-paper:generateScreenUiPack") }
tasks.test {
    systemProperty("screenUiPackDirectory", project(":vexcore-paper").layout.buildDirectory.dir("generated/screen-ui-pack").get().asFile.absolutePath)
}

dependencies {
    implementation(project(":vexcore-screen-ui:common"))
    testImplementation(project(":vexcore-screen-ui:versions:v26_2"))
    api(project(":vexcore-paper-api"))
    implementation(project(":vexcore-common"))
    implementation(project(":vexcore-nms:common"))

    compileOnly("io.papermc.paper:paper-api:26.2.build.84-stable")
    compileOnly("me.clip:placeholderapi:2.12.2")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    testImplementation("io.papermc.paper:paper-api:26.2.build.84-stable")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.github.ben-manes.caffeine:caffeine:3.2.4")
}
