plugins {
    id("dev.arbjerg.lavalink.gradle-plugin") version "1.1.2"
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.sedmelluq" && requested.name == "lavaplayer") {
            useTarget("dev.arbjerg:lavaplayer:2.2.7")
            because("Using lavalink-devs fork of lavaplayer")
        }
    }
}

base {
    archivesName.set("soundy-plugin")
}

dependencies {
    implementation(project(":source"))
    implementation(project(":common"))
    compileOnly("dev.arbjerg:lavaplayer:2.2.7")
    compileOnly("dev.arbjerg.lavalink:plugin-api:4.2.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

tasks.jar {
    archiveBaseName.set("soundy-plugin")
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(tasks.named("generatePluginProperties"))
}
