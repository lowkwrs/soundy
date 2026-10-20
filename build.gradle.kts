plugins {
    java
    `maven-publish`
    id("dev.arbjerg.lavalink.gradle-plugin") version "1.0.16" apply false
}

allprojects {
    group = "com.waris4ly.soundy"
    version = "1.1.0"

    repositories {
        mavenCentral()
        maven("https://maven.lavalink.dev/releases")
        maven("https://jitpack.io")
    }
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependencies {
        compileOnly("dev.arbjerg:lavaplayer:2.2.6")
    }
}

// plugin module has its own publishing via lavalink gradle plugin
configure(subprojects.filter { it.name != "plugin" }) {
    apply(plugin = "maven-publish")

    configure<PublishingExtension> {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/waris4ly/soundy")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                groupId = "com.waris4ly.soundy"
                artifactId = project.name
                version = project.version.toString()
            }
        }
    }
}
