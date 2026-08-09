plugins {
    `java-library`
}

dependencies {
    api(project(":vexcore-api"))
    api(project(":vexcore-items:common"))
    api(project(":vexcore-packets:common"))
    implementation(platform("net.kyori:adventure-bom:5.2.0"))
    implementation("net.kyori:adventure-text-minimessage")
    compileOnly("io.papermc.paper:paper-api:26.2.build.84-stable")

    testImplementation("io.papermc.paper:paper-api:26.2.build.84-stable")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
