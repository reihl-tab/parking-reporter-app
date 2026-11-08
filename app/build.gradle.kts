plugins {
    id("com.google.devtools.ksp")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "il.sidewalks.reporter"
    compileSdk = 37
    defaultConfig {
        applicationId = "il.sidewalks.reporter"
        minSdk = 26
        targetSdk = 37
        if (providers.gradleProperty("previewArm64").isPresent) { ndk { abiFilters += "arm64-v8a" } }
        versionCode = 37
        versionName = "0.6.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { abortOnError = true }
    sourceSets { getByName("test") { resources.srcDir("src/main/assets") } }
    buildTypes {
        debug {
            isTestCoverageEnabled = true
        }
    }
}

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
    }
    val generatedFilters = listOf("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*")
    classDirectories.setFrom(fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) { exclude(generatedFilters) })
    sourceDirectories.setFrom("src/main/java", "src/main/kotlin")
    executionData.setFrom(fileTree(layout.buildDirectory) { include("outputs/unit_test_code_coverage/debugUnitTest/*.exec") })
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation("com.google.crypto.tink:tink-android:1.24.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.1")
    testImplementation("org.json:json:20260814")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
