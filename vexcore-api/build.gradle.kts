plugins {
    `java-library`
}

dependencies {
    api("org.jetbrains:annotations:26.0.2-1")
    api(platform("net.kyori:adventure-bom:5.2.0"))
    api("net.kyori:adventure-api")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
