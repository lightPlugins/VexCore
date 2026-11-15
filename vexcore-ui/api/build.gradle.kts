plugins {
    `java-library`
}

group = "dev.vexsoft.vexui"

dependencies {
    api(project(":vexcore-paper-api"))
    compileOnly("io.papermc.paper:paper-api:26.3.build.241-stable")
}
