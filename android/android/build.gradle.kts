import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

group = "io.sauti"
version = "0.1.0"

android {
    namespace = "io.sauti.android"
    compileSdk = 35

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    defaultConfig {
        minSdk = 21
        testOptions.targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    api(project(":engine"))
    implementation(libs.stream.webrtc.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)

    androidTestImplementation(libs.stream.webrtc.android)
    androidTestImplementation(libs.kotlinx.coroutines.core)
    androidTestImplementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}

val webrtcAlignmentDir = layout.buildDirectory.dir("webrtc-jni")

val extractWebrtcSo by tasks.registering(Copy::class) {
    val webrtcAar = configurations.getByName("releaseRuntimeClasspath")
        .incoming
        .artifactView { componentFilter { it.displayName.contains("stream-webrtc-android") } }
    from({ webrtcAar.files.map { zipTree(it) } }) {
        include("jni/**/*.so")
    }
    into(webrtcAlignmentDir)
}

val checkSoAlignment by tasks.registering(Exec::class) {
    group = "verification"
    description = "Fails if any packaged 64-bit WebRTC .so is not 16 KB page-aligned."
    dependsOn(extractWebrtcSo)
    workingDir = rootDir
    doFirst {
        commandLine("bash", "scripts/check_so_alignment.sh", webrtcAlignmentDir.get().asFile.absolutePath)
    }
}

val checkSoAlignmentFails by tasks.registering(Exec::class) {
    group = "verification"
    description = "Proves the alignment gate rejects a deliberately misaligned .so."
    workingDir = rootDir
    commandLine(
        "bash",
        "-c",
        "! bash scripts/check_so_alignment.sh scripts/fixtures/libmisaligned.so"
    )
}

tasks.named("check").configure {
    dependsOn(checkSoAlignment, checkSoAlignmentFails)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            artifactId = "android"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
