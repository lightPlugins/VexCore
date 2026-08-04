plugins {
    `java-library`
}

dependencies {
    api(project(":vexcore-api"))
    implementation(project(":vexcore-cache"))
    implementation(project(":vexcore-data"))
    implementation("org.spongepowered:configurate-yaml:4.2.0")
    implementation(platform("net.kyori:adventure-bom:5.2.0"))
    implementation("net.kyori:adventure-text-minimessage")

    testImplementation(project(":vexcore-service-registry"))
    testImplementation("com.github.ben-manes.caffeine:caffeine:3.2.4")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.kyori:adventure-text-serializer-plain")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
