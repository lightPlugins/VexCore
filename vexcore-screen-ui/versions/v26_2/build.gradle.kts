plugins { `java-library` }
group = "dev.vexsoft.screenui"
tasks.processResources {
    from("pack/protocol.properties") { into("dev/vexsoft/core/paper/screenui/v26_2") }
}
dependencies {
    api(project(":vexcore-screen-ui:common"))
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
