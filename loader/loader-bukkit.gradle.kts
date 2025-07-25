import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.kotlin.dsl.named

plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.0"
    id("com.gradleup.shadow")
}

group = "pl.skidamek"
version = project.findProperty("mod_version") ?: "unspecified"

repositories {
    mavenCentral()
    maven {
        name = "spigotmc-repo"
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
    maven {
        name = "dmulloy2-repo"
        url = uri("https://repo.dmulloy2.net/repository/public/")
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.1.0")

    implementation("com.github.LilBroCodes:Commander:1.51")
    implementation(project(":core"))
    
    // Add missing dependencies
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("io.netty:netty-all:4.1.100.Final")
}

val targetJavaVersion = 17
java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion

    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"

    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.build {
    dependsOn(tasks.named<ShadowJar>("shadowJar"))
    dependsOn(tasks.named("copyBuildJar"))
}

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("automodpack-bukkit-${project.version}.jar")

    // Optional: Uncomment to customize dependencies
    /*
    dependencies {
        exclude(dependency("com.google.code.gson:gson"))
        exclude(dependency("org.apache.logging.log4j:log4j-core"))
        exclude(dependency("org.jetbrains:annotations"))
        exclude(dependency("io.netty:netty-all"))
        exclude(dependency("org.tomlj:tomlj"))
        exclude(dependency("com.github.luben:zstd-jni"))
        include(dependency("org.bouncycastle:bcpkix-jdk18on"))
    }
    */
}

val mergedDir = File("${rootProject.projectDir}/merged")
tasks.register("copyBuildJar") {
    doLast {
        val bukkitJar = File("${rootProject.projectDir}/loader/bukkit/build/libs").listFiles()
            ?.single { it.isFile && !it.name.endsWith("-sources.jar") && it.name.endsWith(".jar") && !it.name.contains(Regex("^loader-bukkit.*")) }
        bukkitJar?.copyTo(File("$mergedDir/${bukkitJar.name}"), overwrite = true)
    }
}

tasks.named<xyz.jpenilla.runpaper.task.RunServer>("runServer") {
    version.set("1.20.1")
}
