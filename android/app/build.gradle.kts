import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

val repoRootDir = rootProject.projectDir.parentFile ?: rootProject.projectDir

fun loadDotEnv(file: File): Map<String, String> {
    if (!file.exists()) {
        return emptyMap()
    }

    val values = mutableMapOf<String, String>()
    file.forEachLine { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) {
            return@forEachLine
        }

        val separatorIndex = line.indexOf('=')
        if (separatorIndex <= 0) {
            return@forEachLine
        }

        val key = line.substring(0, separatorIndex).trim()
        var value = line.substring(separatorIndex + 1).trim()
        if (value.length >= 2) {
            val first = value.first()
            val last = value.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length - 1)
            }
        }
        values[key] = value
    }

    return values
}

fun configuredValue(
    gradleProperty: String,
    localProperty: String,
    envValue: String?,
    defaultValue: String,
): String = listOf(
    findProperty(gradleProperty) as String?,
    localProperties.getProperty(localProperty),
    envValue,
    defaultValue,
).firstNotNullOf { candidate ->
    candidate?.trim()?.takeIf { it.isNotBlank() }
}

fun quoted(value: String): String = "\"${value}\""

fun validateRequiredUrl(name: String, value: String): String {
    val normalized = value.trim().removeSuffix("/")
    require(normalized.isNotBlank()) {
        "$name must not be blank."
    }
    require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
        "$name must start with http:// or https://."
    }
    return normalized
}

fun validateOptionalUrl(name: String, value: String): String {
    val normalized = value.trim().removeSuffix("/")
    if (normalized.isBlank()) return ""
    require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
        "$name must start with http:// or https:// when configured."
    }
    return normalized
}

val dotEnv = loadDotEnv(repoRootDir.resolve(".env"))
val dmapHostIp = dotEnv["DMAP_HOST_IP"]?.takeIf { it.isNotBlank() }

val mapBackendUrl = validateRequiredUrl(
    name = "dmap.backendUrl",
    value = configuredValue(
        gradleProperty = "dmap.backendUrl",
        localProperty = "dmap.backendUrl",
        envValue = dotEnv["DMAP_BACKEND_URL"]?.takeIf { it.isNotBlank() },
        defaultValue = dmapHostIp?.let { "http://$it:8080" } ?: "http://10.0.2.2:8080",
    ),
)

val searchBackendUrl = validateOptionalUrl(
    name = "dmap.searchBackendUrl",
    value = configuredValue(
        gradleProperty = "dmap.searchBackendUrl",
        localProperty = "dmap.searchBackendUrl",
        envValue = dotEnv["DMAP_SEARCH_BACKEND_URL"]?.takeIf { it.isNotBlank() },
        defaultValue = dmapHostIp?.let { "http://$it:8081" } ?: "http://10.0.2.2:8081",
    ),
)

val routingBackendUrl = validateOptionalUrl(
    name = "dmap.routingBackendUrl",
    value = configuredValue(
        gradleProperty = "dmap.routingBackendUrl",
        localProperty = "dmap.routingBackendUrl",
        envValue = dotEnv["DMAP_ROUTING_BACKEND_URL"]?.takeIf { it.isNotBlank() },
        defaultValue = dmapHostIp?.let { "http://$it:8082" } ?: "",
    ),
)

val imageryBackendUrl = validateOptionalUrl(
    name = "dmap.imageryBackendUrl",
    value = configuredValue(
        gradleProperty = "dmap.imageryBackendUrl",
        localProperty = "dmap.imageryBackendUrl",
        envValue = dotEnv["DMAP_IMAGERY_BACKEND_URL"]?.takeIf { it.isNotBlank() },
        defaultValue = dmapHostIp?.let { "http://$it:8083" } ?: "http://10.0.2.2:8083",
    ),
)

android {
    namespace = "com.dmap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dmap"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "MAP_BACKEND_URL", quoted(mapBackendUrl))
        buildConfigField("String", "SEARCH_BACKEND_URL", quoted(searchBackendUrl))
        buildConfigField("String", "ROUTING_BACKEND_URL", quoted(routingBackendUrl))
        buildConfigField("String", "IMAGERY_BACKEND_URL", quoted(imageryBackendUrl))

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    implementation("org.maplibre.gl:android-sdk:13.0.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
