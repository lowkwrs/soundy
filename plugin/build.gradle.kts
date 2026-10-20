plugins {
    id("dev.arbjerg.lavalink.gradle-plugin") version "1.0.16"
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.sedmelluq" && requested.name == "lavaplayer") {
            useTarget("dev.arbjerg:lavaplayer:2.2.6")
            because("Using lavalink-devs fork of lavaplayer")
        }
    }
}

dependencies {
    implementation(project(":source"))
    implementation(project(":common"))
    compileOnly("dev.arbjerg:lavaplayer:2.2.6")
    compileOnly("dev.arbjerg.lavalink:plugin-api:4.2.2")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

tasks.jar {
    archiveBaseName.set("soundy-plugin")
}
