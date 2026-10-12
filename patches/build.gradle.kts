// Modified 2026-10-12 by Moon Patches contributors. Based on Morphe Patches; see NOTICE.
import org.gradle.api.tasks.JavaExec

group = "app.morphe"

patches {
    about {
        name = "Moon Patches"
        description = "YouTube watch-together rooms and Moon branding, based on Morphe Patches"
        source = "https://github.com/noire342/moon-patches"
        author = "Moon Patches contributors"
        contact = "https://github.com/noire342/moon-patches/issues"
        website = "https://github.com/noire342/moon-patches"
        license = "GNU General Public License v3.0, with additional GPL section 7 requirements"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")
val jamApk = providers.gradleProperty("jamApk")
val jamOutput = providers.gradleProperty("jamOutput")

dependencies {
    // Required due to smali, or build fails. Can be removed once smali is bumped.
    implementation(libs.guava)

    implementation(libs.morphe.patches.library)

    patchListGeneratorClasspath(libs.gson)

    // Android API stubs defined here.
    compileOnly(project(":patches:stub"))

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
}

tasks {
    test {
        useJUnitPlatform()
        jamApk.orNull?.let { systemProperty("jamApk", it) }
    }

    register<JavaExec>("checkStringResources") {
        description = "Checks resource strings for invalid formatting"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.patches.util.resource.CheckStringResourcesKt")
    }

    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }

    register<JavaExec>("validateJam") {
        description = "Patches a supplied YouTube Music APK for Jam validation and optional device testing"
        group = "verification"

        dependsOn("testClasses")
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("app.morphe.patches.music.interaction.jam.JamDeviceBuildKt")

        doFirst {
            val apk = jamApk.orNull
                ?: throw GradleException("validateJam requires -PjamApk=/absolute/path/to/ytm.apk")
            setArgs(listOfNotNull(apk, jamOutput.orNull))
        }
    }
    register<JavaExec>("scanRoomPlayer") {
        dependsOn("testClasses")
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("app.morphe.patches.youtube.interaction.rooms.ScanRoomPlayerKt")
        doFirst { setArgs(listOf(providers.gradleProperty("roomsApk").get())) }
    }
    register<JavaExec>("validateRooms") {
        group = "verification"
        dependsOn("testClasses")
        classpath = sourceSets["test"].runtimeClasspath
        mainClass.set("app.morphe.patches.youtube.interaction.rooms.RoomsDeviceBuildKt")
        maxHeapSize = "3g"
        doFirst { setArgs(listOfNotNull(providers.gradleProperty("roomsApk").orNull ?: error("roomsApk is required"), providers.gradleProperty("roomsOutput").orNull)) }
    }
    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
