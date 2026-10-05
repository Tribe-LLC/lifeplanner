import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.performance)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

val localProperties: Properties = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) propsFile.inputStream().use { load(it) }
}

fun localProp(key: String): String? =
    localProperties[key]?.toString() ?: project.findProperty(key) as? String

// Resolve the release keystore. Set RELEASE_STORE_FILE in local.properties to point at it, given as
// an absolute path, a ~-relative path, or a path relative to the repo root. Defaults to
// lifeplanner.jks at the repo root. This lets the keystore live outside the repo (e.g.
// ~/Documents/tribe/lifeplanner.jks) without copying it in.
fun resolveKeystoreFile(): File {
    val configured = localProp("RELEASE_STORE_FILE")?.trim()?.takeIf { it.isNotEmpty() }
        ?: return rootProject.file("lifeplanner.jks")
    val expanded = if (configured.startsWith("~/")) {
        System.getProperty("user.home") + configured.substring(1)
    } else configured
    val f = File(expanded)
    return if (f.isAbsolute) f else rootProject.file(expanded)
}

android {

    signingConfigs {
        val keystoreFile = resolveKeystoreFile()
        val releaseStorePassword = localProp("RELEASE_STORE_PASSWORD")
        val releaseKeyAlias = localProp("RELEASE_KEY_ALIAS")
        val releaseKeyPassword = localProp("RELEASE_KEY_PASSWORD")
        val credentialsPresent = !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank()

        // All four or none. A keystore with no passwords used to produce a signing config with
        // null credentials, and that does not fail where you would look for it: the build runs for
        // three minutes and then dies inside bundletool on signReleaseBundle with a bare
        // NullPointerException and no message. Pointing RELEASE_STORE_FILE at the real keystore
        // before setting the passwords is enough to hit it, and so is a CI run missing one secret.
        // Without the credentials we fall back to the documented behaviour instead: the AAB still
        // builds, unsigned, and says so.
        if (keystoreFile.exists() && credentialsPresent) {
            create("release") {
                storeFile = keystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        } else if (keystoreFile.exists()) {
            logger.warn(
                "Release signing skipped: found the keystore at ${keystoreFile.path} but " +
                    "RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD are not all " +
                    "set. The AAB will build UNSIGNED and cannot be uploaded to Play."
            )
        }
    }

    namespace = "az.tribe.lifeplanner"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "az.tribe.lifeplanner"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = libs.versions.app.versionCode.get().toInt()
        versionName = libs.versions.app.versionName.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keystoreFile = resolveKeystoreFile()
            if (keystoreFile.exists() && signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(projects.app.shared)

    debugImplementation(compose.uiTooling)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Firebase BOM (gradle plugins for crashlytics/perf need the actual SDK on the classpath)
    implementation(platform(libs.firebase.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Google Play services
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)
    implementation(libs.play.review)
    implementation(libs.play.review.ktx)

    // Health Connect (needed at app link time)
    implementation(libs.health.connect)
}
