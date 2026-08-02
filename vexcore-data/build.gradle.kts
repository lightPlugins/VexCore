plugins {
    `java-library`
}

dependencies {
    api(project(":vexcore-api"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.postgresql:postgresql:42.7.8")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
