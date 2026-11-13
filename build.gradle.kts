plugins {
    java
    `maven-publish`
    id("dev.arbjerg.lavalink.gradle-plugin") version "1.1.2" apply false
    id("com.diffplug.spotless") version "7.0.2"
}

allprojects {
    group = "com.waris4ly.soundy"
    version = "1.0.4"

    repositories {
        mavenCentral()
        maven("https://maven.lavalink.dev/releases")
        maven("https://jitpack.io")
    }

    apply(plugin = "com.diffplug.spotless")
    spotless {
        java {
            target("src/**/*.java")
            removeUnusedImports()
            eclipse()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<Javadoc> {
        (options as StandardJavadocDocletOptions).apply {
            addStringOption("Xdoclint:none", "-quiet")
        }
    }

    dependencies {
        compileOnly("dev.arbjerg:lavaplayer:2.2.7")
    }
}

configure(subprojects.filter { it.name != "plugin" }) {
    apply(plugin = "maven-publish")

    configure<PublishingExtension> {
        repositories {
            maven {
                name = "GitHubPackages"
                val repoOwner = System.getenv("GITHUB_REPOSITORY_OWNER") ?: "waris4ly"
                url = uri("https://maven.pkg.github.com/$repoOwner/soundy-plugin")
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

                pom {
                    name.set(project.name)
                    description.set("SoundCloud source manager for Lavaplayer")
                    url.set("https://github.com/waris4ly/soundy-plugin")
                    scm {
                        connection.set("scm:git:git://github.com/waris4ly/soundy-plugin.git")
                        developerConnection.set("scm:git:ssh://github.com:waris4ly/soundy-plugin.git")
                        url.set("https://github.com/waris4ly/soundy-plugin")
                    }
                }
            }
        }
    }
}
