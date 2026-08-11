plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.remotex.feature.watch"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.media3.exoplayer)
    implementation(project(":feature:ssh"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
