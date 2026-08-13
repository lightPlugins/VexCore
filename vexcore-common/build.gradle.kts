plugins {
    `java-library`
}

dependencies {
    api(project(":vexcore-api"))

    implementation("org.spongepowered:configurate-yaml:4.2.0")
    implementation(platform("net.kyori:adventure-bom:5.2.0"))
    implementation("net.kyori:adventure-text-minimessage")
    implementation("com.ezylang:EvalEx:3.7.0")

    compileOnly("com.github.ben-manes.caffeine:caffeine:3.2.4")
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    compileOnly("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.2")
    compileOnly("com.zaxxer:HikariCP:6.3.0")
    compileOnly("org.postgresql:postgresql:42.7.8")
    compileOnly("org.xerial:sqlite-jdbc:3.53.0.0")

    testImplementation("com.github.ben-manes.caffeine:caffeine:3.2.4")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.2")
    testImplementation("org.xerial:sqlite-jdbc:3.53.0.0")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.kyori:adventure-text-serializer-plain")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
