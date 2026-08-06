plugins {
    `java-library`
}

dependencies {
    api(project(":vexcore-api"))
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.21.2")

    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    testImplementation(project(":vexcore-service-registry"))
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
