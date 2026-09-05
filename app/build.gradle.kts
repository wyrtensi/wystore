import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Release signing material, read from a file that is never committed.
 *
 * Wy Store checks its own signature when it updates itself, so every published build has to carry
 * the same certificate; a release signed by whoever happens to be building is not installable over
 * the previous one. Credentials come from `keystore.properties` at the repository root or, in CI,
 * from the matching environment variables. Without either, the release build stays unsigned rather
 * than silently falling back to the debug key.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, environmentName: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(environmentName)

android {
    namespace = "dev.wystore"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.wystore"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "0.1.11"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("storeFile", "WYSTORE_KEYSTORE")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = signingValue("storePassword", "WYSTORE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "WYSTORE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "WYSTORE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
                .takeIf { it.storeFile?.exists() == true }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Navigation and action icons; Material 3 does not bring the icon set transitively.
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.androidx.room.compiler)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.gson)
    implementation(libs.coil.compose)
    // RuStore serves its category icons as SVG, which Coil cannot decode without this.
    implementation(libs.coil.svg)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.room.testing)

    // room-testing reads exported schema JSON through kotlinx-serialization. The app already
    // resolves that library transitively at an older version, and Gradle's consistent resolution
    // pushes it onto the androidTest classpath, where the older runtime cannot load the serializers
    // room-migration was compiled against. Raising the floor keeps both classpaths on one version
    // without adding a new production dependency.
    constraints {
        implementation(libs.kotlinx.serialization.json)
    }
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Exported Room schemas are the input to the migration test.
android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
