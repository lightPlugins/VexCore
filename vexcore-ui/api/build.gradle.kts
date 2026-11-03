plugins {
    `java-library`
}

group = "dev.vexsoft.vexui"

dependencies {
    api(project(":vexcore-paper-api"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.84-stable")
}
