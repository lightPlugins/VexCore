plugins {
    `java-library`
}

dependencies {
    api(project(":vexcore-api"))
    compileOnly("com.github.ben-manes.caffeine:caffeine:3.2.4")

    testImplementation("com.github.ben-manes.caffeine:caffeine:3.2.4")
    testImplementation(project(":vexcore-service-registry"))
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
