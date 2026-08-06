plugins {
    `java-library`
}

dependencies {
    api(project(":vexcore-api"))
    compileOnly("com.velocitypowered:velocity-api:4.1.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:4.1.0-SNAPSHOT")
}
