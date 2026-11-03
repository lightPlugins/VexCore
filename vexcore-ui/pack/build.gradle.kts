import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    `java-library`
}

group = "dev.vexsoft.vexui"

val packDirectory = layout.buildDirectory.dir("generated/pack")
val metricsDirectory = layout.buildDirectory.dir("generated/metrics")
val clientJar = layout.buildDirectory.file("minecraft/26.2-client.jar")
val unicodeFont = layout.buildDirectory.file("unicode/unifont-26.2.zip")
val germanTranslations = layout.buildDirectory.file("translations/de_de.json")
val prepareVexUITranslations = tasks.register("prepareVexUITranslations") {
    val supplied = providers.gradleProperty("vexuiGermanTranslations").map { rootProject.file(it) }
    inputs.file(supplied).optional()
    outputs.file(germanTranslations)
    doLast {
        val target = germanTranslations.get().asFile
        val cache = rootProject.file(".gradle/vexui/vanilla-de_de-26.2.json")
        val hash = "a9b76c1f25d74ef647ff6025849faf7e86e97973"
        val source = supplied.orNull ?: if (cache.isFile) cache else run {
            check(!gradle.startParameter.isOffline) { "Build VexUI translations once online or provide a local file." }
            cache.parentFile.mkdirs()
            val partial = cache.resolveSibling(cache.name + ".part")
            URI("https://resources.download.minecraft.net/${hash.take(2)}/$hash").toURL().openStream().use { input ->
                partial.outputStream().use { input.copyTo(it) }
            }
            partial
        }
        val actual = MessageDigest.getInstance("SHA-1").digest(source.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        check(actual == hash) { "Unexpected Minecraft 26.2 German translation SHA-1" }
        cache.parentFile.mkdirs()
        target.parentFile.mkdirs()
        if (source != cache) Files.copy(source.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING)
        Files.copy(cache.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
val prepareVexUIUnicode = tasks.register("prepareVexUIUnicode") {
    val supplied = providers.gradleProperty("vexuiUnicodeZip").map { rootProject.file(it) }
    inputs.file(supplied).optional()
    outputs.file(unicodeFont)
    doLast {
        val target = unicodeFont.get().asFile
        // Different composite-build Gradle versions may clean one another's task outputs. Keep downloaded
        // sources outside those outputs so a second offline build can restore its generated input safely.
        val cache = rootProject.layout.projectDirectory.file(".gradle/vexui/unifont-26.2.zip").asFile
        val source = if (target.isFile) target else supplied.orNull ?: if (cache.isFile) cache else run {
            check(!gradle.startParameter.isOffline) { "Build VexUI Unicode once online before using --offline." }
            cache.parentFile.mkdirs()
            val partial = cache.resolveSibling(cache.name + ".part")
            URI("https://resources.download.minecraft.net/cc/ccd5ac4767ce0a9c71d1dd62f2dc25449789b5dd")
                .toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            partial
        }
        val hash = MessageDigest.getInstance("SHA-1").digest(source.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        check(hash == "ccd5ac4767ce0a9c71d1dd62f2dc25449789b5dd") { "Unexpected vanilla Unicode SHA-1" }
        if (source != cache) {
            cache.parentFile.mkdirs()
            Files.copy(source.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        if (source != target) {
            target.parentFile.mkdirs()
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
val prepareVexUIClient = tasks.register("prepareVexUIClient") {
    val supplied = providers.gradleProperty("vexuiClientJar").map { rootProject.file(it) }
    inputs.file(supplied).optional()
    outputs.file(clientJar)
    doLast {
        val target = clientJar.get().asFile
        val source = if (target.isFile) target else supplied.orNull ?: run {
            check(!gradle.startParameter.isOffline) {
                "Build VexUI once online or pass -PvexuiClientJar=<official-26.2-client.jar>."
            }
            target.parentFile.mkdirs()
            val partial = target.resolveSibling(target.name + ".part")
            URI("https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar")
                .toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            partial
        }
        val hash = MessageDigest.getInstance("SHA-1").digest(source.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        check(hash == "2dc72797acbc1b63fc16a11c4ac393605f453754") { "Unexpected Minecraft 26.2 client SHA-1" }
        if (source != target) {
            target.parentFile.mkdirs()
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

val generateVexUIPack = tasks.register<JavaExec>("generateVexUIPack") {
    group = "build"
    description = "Generates the versioned VexUI theme, vanilla icon fonts and matching runtime metrics."
    dependsOn(tasks.classes, prepareVexUIClient, prepareVexUIUnicode, prepareVexUITranslations)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.vexsoft.core.vexui.pack.VexUIPackGenerator")
    inputs.file(clientJar)
    inputs.file(unicodeFont)
    inputs.file(germanTranslations)
    inputs.dir(layout.projectDirectory.dir("src/main/java"))
    outputs.dir(packDirectory)
    outputs.dir(metricsDirectory)
    systemProperty("java.awt.headless", "true")
    doFirst {
        val buildRoot = layout.buildDirectory.get().asFile.canonicalFile.toPath()
        val targets = listOf(packDirectory.get().asFile, metricsDirectory.get().asFile)
        check(targets.all { it.canonicalFile.toPath().startsWith(buildRoot) }) {
            "VexUI generated directories must stay inside the module build directory"
        }
        project.delete(targets)
    }
    args(packDirectory.get().asFile.absolutePath, metricsDirectory.get().asFile.absolutePath,
        clientJar.get().asFile.absolutePath, unicodeFont.get().asFile.absolutePath,
        germanTranslations.get().asFile.absolutePath)
}

val vexUIResourcePack = tasks.register<Zip>("vexUIResourcePack") {
    group = "build"
    dependsOn(generateVexUIPack)
    from(packDirectory)
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    archiveFileName.set("VexCore-VexUI-26.2.zip")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.register<JavaExec>("previewRewardCards") {
    group = "verification"
    description = "Renders an offline art preview from the actual reward-card pack assets."
    dependsOn(generateVexUIPack)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.vexsoft.core.vexui.pack.PreviewRewardCards")
    systemProperty("java.awt.headless", "true")
    args(packDirectory.get().asFile.absolutePath, layout.buildDirectory.file("reports/reward-cards.png").get().asFile.absolutePath)
}
tasks.assemble {
    dependsOn(vexUIResourcePack)
}

dependencies {
    implementation(project(":vexcore-ui:api"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    dependsOn(prepareVexUIClient, prepareVexUIUnicode)
    systemProperty("vexui.client", clientJar.get().asFile.absolutePath)
    systemProperty("vexui.unicode", unicodeFont.get().asFile.absolutePath)
}

