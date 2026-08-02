plugins {
    `java-library`
}

dependencies {
    api(project(":vexcore-api"))
    implementation("org.spongepowered:configurate-yaml:4.2.0")

    testImplementation(project(":vexcore-service-registry"))
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
