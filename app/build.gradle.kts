plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.remotex.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.remotex.android"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.environmentVariable("REMOTEX_VERSION_CODE")
            .orNull
            ?.toIntOrNull()
            ?.coerceAtLeast(1)
            ?: 1
        versionName = providers.environmentVariable("REMOTEX_VERSION_NAME").orNull ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }

    val releaseKeystorePath = providers.environmentVariable("REMOTEX_KEYSTORE_PATH").orNull
    val releaseKeystorePassword = providers.environmentVariable("REMOTEX_KEYSTORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("REMOTEX_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("REMOTEX_KEY_PASSWORD").orNull
    val releaseSigningAvailable = listOf(
        releaseKeystorePath,
        releaseKeystorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = false
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:security"))
    implementation(project(":core:logging"))
    implementation(project(":core:ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:connections"))
    implementation(project(":feature:vnc"))
    implementation(project(":feature:ssh"))
    implementation(project(":feature:sftp"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.compose.ui.tooling.preview)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
