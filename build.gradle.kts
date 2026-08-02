plugins {
    base
}

allprojects {
    group = "dev.vexsoft"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.spongepowered.org/repository/maven-public/")
    }
}

subprojects {
    plugins.withId("java") {
        dependencies {
            add("compileOnly", "org.projectlombok:lombok:1.18.42")
            add("annotationProcessor", "org.projectlombok:lombok:1.18.42")
            add("testCompileOnly", "org.projectlombok:lombok:1.18.42")
            add("testAnnotationProcessor", "org.projectlombok:lombok:1.18.42")
        }

        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
            withSourcesJar()
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}
