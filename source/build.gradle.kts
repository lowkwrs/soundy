dependencies {
    implementation(project(":common"))
    compileOnly("dev.arbjerg:lavaplayer:2.2.7")
    compileOnly("org.slf4j:slf4j-api:2.0.13")

    testImplementation("dev.arbjerg:lavaplayer:2.2.7")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
