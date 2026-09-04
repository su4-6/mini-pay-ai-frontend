import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val releaseSigningProperties = listOf(
    "MINIPAY_RELEASE_STORE_FILE",
    "MINIPAY_RELEASE_STORE_PASSWORD",
    "MINIPAY_RELEASE_KEY_ALIAS",
    "MINIPAY_RELEASE_KEY_PASSWORD"
).associateWith { providers.gradleProperty(it).orNull }
val releaseSigningConfigured = releaseSigningProperties.values.all { !it.isNullOrBlank() }
val buildingRelease = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
if (buildingRelease && !releaseSigningConfigured) {
    throw GradleException(
        "Release signing properties are required; see android/gradle.production.properties.example."
    )
}

android {
    namespace = "com.minipay.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.minipay.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.1.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "OAUTH_CLIENT_ID", "\"minipay-android\"")
        buildConfigField("String", "OAUTH_REDIRECT_URI", "\"com.minipay.mobile:/oauth2redirect\"")
        buildConfigField("String", "PAYMENT_BASE_URL", "\"\"")
        buildConfigField("String", "WALLET_BASE_URL", "\"\"")
        val amapApiKey = providers.gradleProperty("MINIPAY_AMAP_ANDROID_KEY")
            .orElse(localProperties.getProperty("MINIPAY_AMAP_ANDROID_KEY", ""))
            .get()
        buildConfigField("String", "AMAP_API_KEY", "\"$amapApiKey\"")
        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseSigningProperties.getValue("MINIPAY_RELEASE_STORE_FILE")!!)
                storePassword = releaseSigningProperties.getValue("MINIPAY_RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningProperties.getValue("MINIPAY_RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningProperties.getValue("MINIPAY_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Defaults target a USB-connected device with `adb reverse`; override all URLs
            // from local.properties for an emulator (10.0.2.2) or a LAN development host.
            val debugIdentityBaseUrl = providers.gradleProperty("MINIPAY_DEBUG_IDENTITY_BASE_URL")
                .orElse(localProperties.getProperty(
                    "MINIPAY_DEBUG_IDENTITY_BASE_URL", "http://127.0.0.1:8081"
                )).get()
            val debugPaymentBaseUrl = providers.gradleProperty("MINIPAY_DEBUG_PAYMENT_BASE_URL")
                .orElse(localProperties.getProperty(
                    "MINIPAY_DEBUG_PAYMENT_BASE_URL", "http://127.0.0.1:8082"
                )).get()
            val debugWalletBaseUrl = providers.gradleProperty("MINIPAY_DEBUG_WALLET_BASE_URL")
                .orElse(localProperties.getProperty(
                    "MINIPAY_DEBUG_WALLET_BASE_URL", "http://127.0.0.1:8083"
                )).get()
            val debugAgentBaseUrl = providers.gradleProperty("MINIPAY_DEBUG_AGENT_BASE_URL")
                .orElse(localProperties.getProperty(
                    "MINIPAY_DEBUG_AGENT_BASE_URL", "http://127.0.0.1:8086"
                )).get()
            val debugCommerceBaseUrl = providers.gradleProperty("MINIPAY_DEBUG_COMMERCE_BASE_URL")
                .orElse(localProperties.getProperty(
                    "MINIPAY_DEBUG_COMMERCE_BASE_URL", "http://127.0.0.1:8085"
                )).get()
            val debugFoodH5Origin = providers.gradleProperty("MINIPAY_DEBUG_FOOD_H5_ORIGIN")
                .orElse(localProperties.getProperty(
                    "MINIPAY_DEBUG_FOOD_H5_ORIGIN", "https://food.minipay.local"
                )).get()
            buildConfigField("String", "IDENTITY_BASE_URL", "\"$debugIdentityBaseUrl\"")
            buildConfigField("String", "PAYMENT_BASE_URL", "\"$debugPaymentBaseUrl\"")
            buildConfigField("String", "WALLET_BASE_URL", "\"$debugWalletBaseUrl\"")
            buildConfigField("String", "AGENT_BASE_URL", "\"$debugAgentBaseUrl\"")
            buildConfigField("String", "COMMERCE_BASE_URL", "\"$debugCommerceBaseUrl\"")
            buildConfigField("String", "FOOD_H5_ORIGIN", "\"$debugFoodH5Origin\"")
            buildConfigField("String", "USER_AGREEMENT_URL", "\"\"")
            buildConfigField("String", "PRIVACY_POLICY_URL", "\"\"")
        }
        release {
            ndk {
                // The public APK targets modern 64-bit Android devices. Keeping a single ABI
                // avoids shipping three unused copies of the large AMap/WebRTC native libraries.
                abiFilters += "arm64-v8a"
            }
            val identityBaseUrl = providers.gradleProperty("MINIPAY_IDENTITY_BASE_URL")
                .orElse("")
                .get()
            val userAgreementUrl = providers.gradleProperty("MINIPAY_USER_AGREEMENT_URL")
                .orElse("")
                .get()
            val privacyPolicyUrl = providers.gradleProperty("MINIPAY_PRIVACY_POLICY_URL")
                .orElse("")
                .get()
            val paymentBaseUrl = providers.gradleProperty("MINIPAY_PAYMENT_BASE_URL").orElse("").get()
            val walletBaseUrl = providers.gradleProperty("MINIPAY_WALLET_BASE_URL").orElse("").get()
            val agentBaseUrl = providers.gradleProperty("MINIPAY_AGENT_BASE_URL").orElse("").get()
            val commerceBaseUrl = providers.gradleProperty("MINIPAY_COMMERCE_BASE_URL").orElse("").get()
            val foodH5Origin = providers.gradleProperty("MINIPAY_FOOD_H5_ORIGIN").orElse("").get()
            buildConfigField("String", "IDENTITY_BASE_URL", "\"$identityBaseUrl\"")
            buildConfigField("String", "USER_AGREEMENT_URL", "\"$userAgreementUrl\"")
            buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
            buildConfigField("String", "PAYMENT_BASE_URL", "\"$paymentBaseUrl\"")
            buildConfigField("String", "WALLET_BASE_URL", "\"$walletBaseUrl\"")
            buildConfigField("String", "AGENT_BASE_URL", "\"$agentBaseUrl\"")
            buildConfigField("String", "COMMERCE_BASE_URL", "\"$commerceBaseUrl\"")
            buildConfigField("String", "FOOD_H5_ORIGIN", "\"$foodH5Origin\"")
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        jniLibs {
            // Compress the large AMap/WebRTC native libraries in the download APK.
            // Android extracts them once during installation, so runtime performance is unchanged.
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

val validateReleaseAuthConfig by tasks.registering {
    group = "verification"
    description = "Rejects release builds without HTTPS identity and legal-document URLs."
    doLast {
        val requiredHttpsProperties = mapOf(
            "MINIPAY_IDENTITY_BASE_URL" to providers.gradleProperty("MINIPAY_IDENTITY_BASE_URL")
                .orElse("")
                .get(),
            "MINIPAY_USER_AGREEMENT_URL" to providers.gradleProperty("MINIPAY_USER_AGREEMENT_URL")
                .orElse("")
                .get(),
            "MINIPAY_PRIVACY_POLICY_URL" to providers.gradleProperty("MINIPAY_PRIVACY_POLICY_URL")
                .orElse("")
                .get(),
            "MINIPAY_PAYMENT_BASE_URL" to providers.gradleProperty("MINIPAY_PAYMENT_BASE_URL").orElse("").get(),
            "MINIPAY_WALLET_BASE_URL" to providers.gradleProperty("MINIPAY_WALLET_BASE_URL").orElse("").get(),
            "MINIPAY_AGENT_BASE_URL" to providers.gradleProperty("MINIPAY_AGENT_BASE_URL").orElse("").get(),
            "MINIPAY_COMMERCE_BASE_URL" to providers.gradleProperty("MINIPAY_COMMERCE_BASE_URL").orElse("").get(),
            "MINIPAY_FOOD_H5_ORIGIN" to providers.gradleProperty("MINIPAY_FOOD_H5_ORIGIN").orElse("").get()
        )
        requiredHttpsProperties.forEach { (name, value) ->
            check(value.startsWith("https://")) {
                "$name must be configured with an HTTPS URL before a release build."
            }
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseAuthConfig)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":bridge-contract"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("io.github.webrtc-sdk:android:144.7559.09")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // AMap's combined artifact keeps the shared core used by location and weather search single-copy.
    implementation("com.amap.api:3dmap-location-search:10.1.200_loc6.4.9_sea9.7.4")
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
