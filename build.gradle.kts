import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension

plugins {
    base
    id("com.github.spotbugs") version "6.5.9" apply false
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
        pluginManager.apply("com.github.spotbugs")

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

        if (project.name != "vexcore-paper") {
            pluginManager.apply("maven-publish")
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("mavenJava") {
                        artifactId = project.path.removePrefix(":").replace(':', '-')
                        from(components["java"])
                    }
                }
            }
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

        extensions.configure<SpotBugsExtension> {
            effort.set(Effort.MAX)
            reportLevel.set(Confidence.MEDIUM)
            excludeFilter.set(rootProject.layout.projectDirectory.file("config/spotbugs-exclude.xml"))
        }

        tasks.withType<SpotBugsTask>().configureEach {
            reports.create("html") {
                required.set(true)
            }
        }
    }
}

val documentedApiProjects = setOf(
    ":vexcore-api",
    ":vexcore-command-api",
    ":vexcore-paper-api",
)

configure(subprojects.filter { it.path in documentedApiProjects }) {
    pluginManager.apply("checkstyle")

    extensions.configure<CheckstyleExtension> {
        toolVersion = "13.9.0"
        configFile = rootProject.file("config/checkstyle-api.xml")
        isIgnoreFailures = false
        maxWarnings = 0
    }

    tasks.withType<Checkstyle>().matching { it.name == "checkstyleTest" }.configureEach {
        enabled = false
    }
}
