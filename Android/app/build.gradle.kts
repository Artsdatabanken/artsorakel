plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    kotlin("kapt")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "no.artsdatabanken.artsorakel"
    compileSdk = 36

    defaultConfig {
        applicationId = "no.artsdatabanken.orakel"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "4.0.0"  // Centralized version number

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Default BuildConfig values for all variants
        buildConfigField("String", "API_BASE_URL", "\"https://ai.artsdatabanken.no/\"")
        buildConfigField("String", "RSS_FEED_URL", "\"https://ai.test.artsdatabanken.no/rss\"")
        buildConfigField("boolean", "IS_RELEASE_BUILD", "false")  // Default to non-release
        buildConfigField("boolean", "ENABLE_LOGGING", "false")
        buildConfigField("long", "NETWORK_TIMEOUT_SECONDS", "60L")

        // Load bearer token from secrets file
        val secretsFile = file("../../shared/config/secrets.json")
        if (secretsFile.exists()) {
            @Suppress("UNCHECKED_CAST")
            val secretsJson = groovy.json.JsonSlurper().parseText(secretsFile.readText()) as Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val apiSecrets = secretsJson["api"] as Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val bearerTokens = apiSecrets["bearerToken"] as Map<String, String>
            buildConfigField("String", "API_BEARER_TOKEN", "\"${bearerTokens["android"]}\"")
        } else {
            buildConfigField("String", "API_BEARER_TOKEN", "\"\"")
            println("WARNING: secrets.json not found. Bearer token will be empty.")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            // Debug builds should send "test" to API
            buildConfigField("boolean", "IS_RELEASE_BUILD", "false")
            buildConfigField("boolean", "ENABLE_LOGGING", "true")

            // Shorter timeouts for development
            buildConfigField("long", "NETWORK_TIMEOUT_SECONDS", "30L")
        }
        
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Release builds should send "Artsorakel x.x.x (android)" to API
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    jvmToolchain(21)
}

// Kapt configurations 
kapt {
    correctErrorTypes = true
    useBuildCache = true
    includeCompileClasspath = false
}

// KSP configuration for Room
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
    kapt(libs.hilt.compiler)

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
    kaptAndroidTest(libs.hilt.compiler)
    
    // Add missing dependencies for instrumented tests
    androidTestImplementation(libs.kotlinx.coroutines.test.v173)
    androidTestImplementation(libs.androidx.core.testing)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlin.test)
}