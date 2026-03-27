plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

// Load configuration from shared config files
val configFile = file("../../shared/config/app_config.json")
val secretsFile = file("../../shared/config/secrets.json")

@Suppress("UNCHECKED_CAST")
val appConfig = if (configFile.exists()) {
    groovy.json.JsonSlurper().parseText(configFile.readText()) as Map<String, Any>
} else {
    println("WARNING: app_config.json not found. Using default values.")
    emptyMap()
}

@Suppress("UNCHECKED_CAST")
val apiConfig = appConfig["api"] as? Map<String, Any> ?: emptyMap()
@Suppress("UNCHECKED_CAST")
val rssFeedConfig = appConfig["rssFeed"] as? Map<String, Any> ?: emptyMap()

val baseUrlDebug = apiConfig["baseUrlDebug"] as? String ?: "https://ai.test.artsdatabanken.no/"
val baseUrlRelease = apiConfig["baseUrlRelease"] as? String ?: "https://ai.artsdatabanken.no/"
val rssFeedUrlDebug = rssFeedConfig["urlDebug"] as? String ?: "https://ai.test.artsdatabanken.no/rss"
val rssFeedUrlRelease = rssFeedConfig["urlRelease"] as? String ?: "https://ai.artsdatabanken.no/rss"

// Load bearer tokens from secrets file
@Suppress("UNCHECKED_CAST")
val secretsJson = if (secretsFile.exists()) {
    groovy.json.JsonSlurper().parseText(secretsFile.readText()) as Map<String, Any>
} else {
    println("WARNING: secrets.json not found. Bearer tokens will be empty.")
    emptyMap()
}
@Suppress("UNCHECKED_CAST")
val apiSecrets = secretsJson["api"] as? Map<String, Any> ?: emptyMap()
@Suppress("UNCHECKED_CAST")
val bearerTokens = apiSecrets["bearerToken"] as? Map<String, String> ?: emptyMap()
@Suppress("UNCHECKED_CAST")
val bearerTokensTest = apiSecrets["bearerTokenTest"] as? Map<String, String> ?: emptyMap()

android {
    namespace = "no.artsdatabanken.artsorakel"
    compileSdk = 36

    defaultConfig {
        applicationId = "no.artsdatabanken.orakel"
        minSdk = 24
        targetSdk = 36
        versionCode = (System.currentTimeMillis() / 1000).toInt()
        versionName = "4.0.2"  // Centralized version number

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Default BuildConfig values (will be overridden by build types)
        buildConfigField("boolean", "IS_RELEASE_BUILD", "false")
        buildConfigField("boolean", "ENABLE_LOGGING", "false")
        buildConfigField("long", "NETWORK_TIMEOUT_SECONDS", "60L")

        // Bearer tokens for both environments
        buildConfigField("String", "API_BEARER_TOKEN", "\"${bearerTokens["android"] ?: ""}\"")
        buildConfigField("String", "API_BEARER_TOKEN_TEST", "\"${bearerTokensTest["android"] ?: ""}\"")
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            // Debug builds use test endpoints
            buildConfigField("String", "API_BASE_URL", "\"$baseUrlDebug\"")
            buildConfigField("String", "RSS_FEED_URL", "\"$rssFeedUrlDebug\"")
            buildConfigField("boolean", "IS_RELEASE_BUILD", "false")
            buildConfigField("boolean", "ENABLE_LOGGING", "true")
            buildConfigField("long", "NETWORK_TIMEOUT_SECONDS", "30L")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Release builds use production endpoints
            buildConfigField("String", "API_BASE_URL", "\"$baseUrlRelease\"")
            buildConfigField("String", "RSS_FEED_URL", "\"$rssFeedUrlRelease\"")
            buildConfigField("boolean", "IS_RELEASE_BUILD", "true")
            buildConfigField("boolean", "ENABLE_LOGGING", "false")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = false
        viewBinding = true
        buildConfig = true  // Enable BuildConfig generation
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
            excludes += "META-INF/NOTICE.md"
        }
    }
}

// KSP configuration for Room and Hilt
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

dependencies {
    // Core and Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- UI Components for View System ---
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx) // For registerForActivityResult

    // ViewModel and Fragment
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    
    // ViewPager2 for image carousel
    implementation(libs.androidx.viewpager2)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Networking (Retrofit + OkHttp + Gson)
    implementation(libs.squareup.retrofit)
    implementation(libs.squareup.converter.gson)
    implementation(libs.squareup.okhttp)
    implementation(libs.squareup.okhttp.logging.interceptor)

    // Image Loading (Coil)
    implementation(libs.coil.kt)


    // ExifInterface for image orientation
    implementation(libs.androidx.exifinterface)

    // Dependency Injection (Hilt)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room database - using KSP for better performance
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Location services
    implementation(libs.play.services.location)

    // Testing (Unit Tests)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.test)
    
    // Testing (Instrumented Tests)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    
    // Add missing dependencies for instrumented tests
    androidTestImplementation(libs.kotlinx.coroutines.test.v173)
    androidTestImplementation(libs.androidx.core.testing)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlin.test)
}