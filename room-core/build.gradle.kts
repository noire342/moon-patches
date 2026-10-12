plugins { `java-library` }

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85")
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
}

tasks.test { useJUnitPlatform() }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.register<JavaExec>("liveProbe") {
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("app.nyanime.rooms.core.RoomLiveProbe")
    maxHeapSize = "192m"
    doFirst { setArgs(listOf(providers.gradleProperty("roomCode").get(), providers.gradleProperty("commandFile").get())) }
}
tasks.register<JavaExec>("liveLobbyProbe") {
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("app.nyanime.rooms.core.RoomLobbyLiveProbe")
    maxHeapSize = "192m"
}
