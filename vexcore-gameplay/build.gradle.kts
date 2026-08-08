plugins {
    `java-library`
}

dependencies {
    api(project(":vexcore-gameplay-api"))
    implementation(project(":vexcore-data"))
    implementation("com.ezylang:EvalEx:3.7.0")

    testImplementation(project(":vexcore-cache"))
    testImplementation(project(":vexcore-service-registry"))
    testRuntimeOnly("com.github.ben-manes.caffeine:caffeine:3.2.4")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
