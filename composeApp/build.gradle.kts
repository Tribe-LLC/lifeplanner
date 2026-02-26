import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import com.codingfeline.buildkonfig.compiler.FieldSpec
import java.util.Properties
import kotlin.apply

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.sqlDelight)
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.performance)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "az.tribe.lifeplanner")
            export(libs.kmpnotifier) {
                transitiveExport = false
            }
            linkerOpts.add("-lsqlite3")
        }
    }

    sourceSets {

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.core.splashscreen)

            implementation(libs.sqldelight.android)
            //Http client
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)

            implementation(libs.accompanist.systemuicontroller)

            implementation(libs.firebase.common.ktx)
            implementation(libs.firebase.auth.ktx)

            // Coroutines for Firebase Tasks
            implementation(libs.kotlinx.coroutines.play.services)

            // WorkManager for background tasks
            implementation(libs.androidx.work.runtime)

            // Glance for home screen widgets
            implementation(libs.androidx.glance)
            implementation(libs.androidx.glance.appwidget)
            implementation(libs.androidx.glance.material3)

        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)

            implementation(libs.sqldelight.coroutines)

            //Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.cio)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.kotlinx.datetime)

            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)

            implementation(libs.firebase.common)
            implementation(libs.gitlive.firebase.config)
            implementation(libs.firebase.crashlytics)
            implementation(libs.firebase.perf)
            implementation(libs.gitlive.firebase.analytics)
            implementation(libs.gitlive.firebase.auth)
            api(libs.kmpnotifier) // in iOS export this library
            //Kermit  for logging
            implementation(libs.kermit)
        }

        iosMain.dependencies {
            // sqlite
            implementation(libs.sqldelight.ios)
            // ktor
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
            implementation(libs.turbine)
        }

        androidUnitTest.dependencies {
            implementation(libs.sqldelight.test)
        }
    }
}

sqldelight {
    databases {
        create("LifePlannerDB") {
            packageName.set("az.tribe.lifeplanner.database")
            schemaOutputDirectory = file("src/commonMain/sqldelight/databases")
            version = 8 // Updated to add custom coaches and coach groups
            generateAsync.set(true)
        }
    }
    linkSqlite = true
}

android {

    signingConfigs {
        val keystoreFile = file("${rootDir}/lifeplanner.jks")
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as? String
                keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as? String
                keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as? String
            }
        }
    }
    namespace = "az.tribe.lifeplanner"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    defaultConfig {
        applicationId = "az.tribe.lifeplanner"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 5
        versionName = "1.3"

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
            val keystoreFile = file("${rootDir}/lifeplanner.jks")
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
    debugImplementation(compose.uiTooling)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
}


buildkonfig {
    packageName = "az.tribe.lifeplanner"

    val localProperties =
        Properties().apply {
            val propsFile = rootProject.file("local.properties")
            if (propsFile.exists()) {
                load(propsFile.inputStream())
            }
        }

    defaultConfigs {
        buildConfigField(
            FieldSpec.Type.STRING,
            "GEMINI_API_KEY",
            localProperties["GEMINI_API_KEY"]?.toString() ?: "",
        )
        buildConfigField(
            FieldSpec.Type.BOOLEAN,
            "isDebug", "true"
        )
    }
}


rootProject.plugins.withType(org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin::class.java) {
    rootProject.the<YarnRootExtension>().yarnLockMismatchReport =
        YarnLockMismatchReport.WARNING // NONE | FAIL
    rootProject.the<YarnRootExtension>().reportNewYarnLock = false // true
    rootProject.the<YarnRootExtension>().yarnLockAutoReplace = false // true
}
