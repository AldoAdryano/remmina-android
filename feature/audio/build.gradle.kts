plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.remotex.feature.audio"
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
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":feature:ssh"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
