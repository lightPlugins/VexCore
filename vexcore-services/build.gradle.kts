plugins {
    `java-library`
}

dependencies {
    api(project(":vexcore-paper-api"))
    implementation(project(":vexcore-common"))

    compileOnly("io.papermc.paper:paper-api:26.2.build.84-stable")

    testImplementation("io.papermc.paper:paper-api:26.2.build.84-stable")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
