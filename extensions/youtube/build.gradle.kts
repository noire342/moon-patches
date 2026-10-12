// Modified 2026-10-12 by Moon Patches contributors. Based on Morphe Patches; see NOTICE.
import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.protobuf)
}

dependencies {
    implementation(project(":room-core"))
    compileOnly(libs.annotation)
    compileOnly(libs.morphe.extensions.library)
    compileOnly(project(":extensions:shared-youtube:library"))
    compileOnly(project(":extensions:shared-youtube:stub"))
    compileOnly(project(":extensions:shared:library"))
    compileOnly(project(":extensions:youtube:stub"))

    implementation(libs.collections4)
    implementation(libs.protobuf.javalite)
}

configure<ApplicationExtension> {
    defaultConfig {
        minSdk = 26
    }
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}
