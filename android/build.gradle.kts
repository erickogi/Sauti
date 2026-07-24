plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
}

val purity by tasks.registering(Exec::class) {
    group = "verification"
    description = "Fails the build if driver/passenger/trip/rider appear in module sources."
    workingDir = rootDir
    commandLine("bash", "scripts/purity.sh")
}

val enginePurity by tasks.registering(Exec::class) {
    group = "verification"
    description = "Fails if the engine imports android/androidx/org.webrtc."
    workingDir = rootDir
    commandLine("bash", "scripts/check_engine_purity.sh")
}

val checkNoComments by tasks.registering(Exec::class) {
    group = "verification"
    description = "Fails if any module Kotlin source carries a code comment."
    workingDir = rootDir
    commandLine("bash", "scripts/check_no_comments.sh")
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        tasks.matching { it.name == "build" }.configureEach {
            dependsOn(purity, enginePurity, checkNoComments)
        }
    }
    plugins.withId("com.android.library") {
        tasks.matching { it.name == "preBuild" }.configureEach {
            dependsOn(purity, checkNoComments)
        }
    }
}
