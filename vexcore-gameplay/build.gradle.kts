plugins {
    `java-library`
}

dependencies {
    api(project(":vexcore-gameplay-api"))
    implementation(project(":vexcore-data"))

    testImplementation(project(":vexcore-cache"))
    testImplementation(project(":vexcore-service-registry"))
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
