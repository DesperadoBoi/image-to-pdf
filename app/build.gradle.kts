plugins {
    alias(libs.plugins.android.application)
}

val releaseSigningPropertyNames = listOf(
    "IMAGETOPDF_STORE_FILE",
    "IMAGETOPDF_STORE_PASSWORD",
    "IMAGETOPDF_KEY_ALIAS",
    "IMAGETOPDF_KEY_PASSWORD"
)

fun externalSigningValue(name: String): String? {
    return providers.gradleProperty(name).orNull?.trim()?.takeIf(String::isNotEmpty)
        ?: providers.environmentVariable(name).orNull?.trim()?.takeIf(String::isNotEmpty)
}

val releaseSigningValues = releaseSigningPropertyNames.associateWith(::externalSigningValue)
val releaseSigningConfigured = releaseSigningValues.values.all { it != null }

android {
    namespace = "com.desperadoboi.imagetopdf"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.desperadoboi.imagetopdf"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val externalReleaseSigningConfig = if (releaseSigningConfigured) {
        signingConfigs.create("release") {
            storeFile = file(releaseSigningValues.getValue("IMAGETOPDF_STORE_FILE")!!)
            storePassword = releaseSigningValues.getValue("IMAGETOPDF_STORE_PASSWORD")
            keyAlias = releaseSigningValues.getValue("IMAGETOPDF_KEY_ALIAS")
            keyPassword = releaseSigningValues.getValue("IMAGETOPDF_KEY_PASSWORD")
        }
    } else {
        null
    }

    buildTypes {
        release {
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (externalReleaseSigningConfig != null) {
                signingConfig = externalReleaseSigningConfig
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.exifinterface)
    implementation(libs.material)
    implementation(libs.recyclerview)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

val checkReleaseSigningConfiguration by tasks.registering {
    group = "verification"
    description = "Checks that release signing credentials are complete and external."

    doLast {
        val missingProperties = releaseSigningValues
            .filterValues { it == null }
            .keys
            .sorted()
        if (missingProperties.isNotEmpty()) {
            throw GradleException(
                "Release signing is not configured. Define these values in user-level " +
                    "Gradle properties or environment variables: " +
                    missingProperties.joinToString(", ")
            )
        }

        val storeFile = file(releaseSigningValues.getValue("IMAGETOPDF_STORE_FILE")!!)
            .canonicalFile
        if (!storeFile.isFile) {
            throw GradleException(
                "The file supplied by IMAGETOPDF_STORE_FILE does not exist or is not a file."
            )
        }
        if (storeFile.toPath().startsWith(rootProject.projectDir.canonicalFile.toPath())) {
            throw GradleException("The release keystore must be stored outside the repository.")
        }
    }
}

tasks.matching {
    it.name == "packageReleaseBundle" || it.name == "validateSigningRelease"
}.configureEach {
    dependsOn(checkReleaseSigningConfiguration)
}

tasks.register("verifyReleaseBundle") {
    group = "verification"
    description = "Builds and validates the signed production release bundle."
    dependsOn("bundleRelease")

    doLast {
        val bundleFile = layout.buildDirectory
            .file("outputs/bundle/release/app-release.aab")
            .get()
            .asFile
        check(bundleFile.isFile) { "Release AAB was not created." }
        check(bundleFile.length() > 0L) { "Release AAB is empty." }
        check(android.defaultConfig.applicationId == "com.desperadoboi.imagetopdf") {
            "Unexpected release applicationId."
        }
        check(android.defaultConfig.versionCode == 1) { "Unexpected release versionCode." }
        check(android.defaultConfig.versionName == "1.0.0") {
            "Unexpected release versionName."
        }
        check(!android.buildTypes.getByName("release").isDebuggable) {
            "Release variant must not be debuggable."
        }
        logger.lifecycle(
            "Verified release variant AAB: {} ({} bytes)",
            bundleFile,
            bundleFile.length()
        )
    }
}
