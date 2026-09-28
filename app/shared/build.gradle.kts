import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.codingfeline.buildkonfig.compiler.FieldSpec
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.sqlDelight)
    alias(libs.plugins.buildkonfig)
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "kotlin.time.ExperimentalTime"
        )
    }

    // On CI compile all 3 iOS targets. Locally compile arm64 (device) + simulator arm64.
    // iosX64 (Intel simulator) is skipped locally to avoid unnecessary compile time.
    val isCI = System.getenv("CI") != null
    val iosTargets = if (isCI) listOf(iosX64(), iosArm64(), iosSimulatorArm64()) else listOf(iosArm64(), iosSimulatorArm64())
    iosTargets.forEach { iosTarget ->
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

    androidLibrary {
        namespace = "az.tribe.lifeplanner.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            // Firebase BOM — supplies versions for the transitive com.google.firebase:*
            // artifacts that the dev.gitlive:firebase-* libs declare without versions.
            // Required here (not just in androidApp) because this library module resolves
            // those Android artifacts on its own compile/runtime classpath.
            implementation(project.dependencies.platform(libs.firebase.bom))

            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.core.splashscreen)

            implementation(libs.sqldelight.android)
            //Http client
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.cio)
            implementation(libs.koin.android)


            implementation(libs.firebase.common.ktx)

            // Coroutines for Firebase Tasks (still used by Firebase Crashlytics/Perf)
            implementation(libs.kotlinx.coroutines.play.services)

            // WorkManager for background tasks
            implementation(libs.androidx.work.runtime)

            // Glance for home screen widgets
            implementation(libs.androidx.glance)
            implementation(libs.androidx.glance.appwidget)
            implementation(libs.androidx.glance.material3)

            // Facebook SDK (marketing & ads)
            implementation(libs.facebook.android.sdk)

            // PostHog (product analytics)
            implementation(libs.posthog.android)

            // Health Connect — referenced by Android HealthDataManager actual
            implementation(libs.health.connect)

            // Play in-app review (referenced by Android InAppReviewManager actual)
            implementation(libs.play.review)
            implementation(libs.play.review.ktx)

            // Play in-app update (referenced by Android InAppUpdate actual)
            implementation(libs.play.app.update)
            implementation(libs.play.app.update.ktx)

            // Libs with no JVM artifact — duplicated in iosMain.dependencies below.
            implementation(libs.firebase.crashlytics)
            implementation(libs.cmpcharts)
            implementation(libs.compose.mediaplayer)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.phosphor.icons)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor)
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
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.kotlinx.datetime)

            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)

            implementation(libs.firebase.common)
            implementation(libs.gitlive.firebase.config)
            implementation(libs.firebase.perf)
            implementation(libs.gitlive.firebase.analytics)

            // Supabase
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.gotrue)
            implementation(libs.supabase.compose.auth)

            api(libs.kmpnotifier) // in iOS export this library
            //Kermit  for logging
            implementation(libs.kermit)
        }

        iosMain.dependencies {
            // sqlite
            implementation(libs.sqldelight.ios)
            // ktor
            implementation(libs.ktor.client.darwin)
            // HealthKit wrapper
            implementation(libs.health.kmp)
            // Libs with no JVM artifact — duplicated in androidMain.dependencies above.
            implementation(libs.firebase.crashlytics)
            implementation(libs.cmpcharts)
            implementation(libs.compose.mediaplayer)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
        }

        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.test)
            // JVM screenshot previews: render commonMain composables to PNG without an
            // emulator (previews/PreviewScreenshots.kt). Robolectric native graphics + Roborazzi.
            implementation(libs.junit)
            implementation(libs.robolectric)
            // Already on the test runtime classpath via sqldelight's android-driver, but not the
            // compile one. DatabaseMigrationsTest needs it to open a real SupportSQLiteDatabase.
            implementation(libs.androidx.sqlite.framework)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}

sqldelight {
    databases {
        create("LifePlannerDB") {
            packageName.set("az.tribe.lifeplanner.database")
            schemaOutputDirectory = file("src/commonMain/sqldelight/databases")
            version = 43 // 22: HabitEntity.unit, 23: CachedPersonaEntity, 24: HabitCheckInEntity.count, 26: CachedPersonaEntity.slug+avatar_url, 27: UserSituationEntity, 28: ScreenTimeEventEntity + UserActivityPattern behavioral columns, 29: LifeValueEntity table, 30: GoalEntity.valueId, 31: DecisionEntity table, 32: Goal.predictedDueDate + Milestone.estimatedEffort, 33: IdentityStatementEntity table, 34: DecisionProfileEntity table, 35: HabitEntity.healthMetricType+healthTarget, 36: KnowledgeReadEntity table (Learn hub read-state), 37: DecisionEntity.source+status (journal-detected decisions await confirmation), 38: HabitEntity.completionSource (internal sessions auto-complete linked habits), 39: KnowledgeLesson/KnowledgeCollection content cache (Learn lessons authored in Supabase), 40: WheelScoreEntity (Wheel of Life, user-set scores only), 41: WheelSnapshotEntity (wheel history, one row per day), 42: GoalEntity.wheelArea (goal's why as a wheel area), 43: v4 LifeLogEntity + BudgetEntity + TripEntity + TripItemEntity
            generateAsync.set(true)
        }
    }
    linkSqlite = true
}

val localProperties: Properties = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) propsFile.inputStream().use { load(it) }
}

buildkonfig {
    packageName = "az.tribe.lifeplanner"

    defaultConfigs {
        buildConfigField(
            FieldSpec.Type.BOOLEAN,
            "isDebug", "true"
        )
        buildConfigField(
            FieldSpec.Type.STRING,
            "SUPABASE_URL",
            localProperties["SUPABASE_URL"]?.toString() ?: "",
        )
        buildConfigField(
            FieldSpec.Type.STRING,
            "SUPABASE_ANON_KEY",
            localProperties["SUPABASE_ANON_KEY"]?.toString() ?: "",
        )
        buildConfigField(
            FieldSpec.Type.STRING,
            "POSTHOG_API_KEY",
            localProperties["POSTHOG_API_KEY"]?.toString() ?: "",
        )
        buildConfigField(
            FieldSpec.Type.STRING,
            "POSTHOG_HOST",
            localProperties["POSTHOG_HOST"]?.toString() ?: "https://us.i.posthog.com",
        )
        buildConfigField(
            FieldSpec.Type.STRING,
            "PERSONA_API_SECRET",
            localProperties["PERSONA_API_SECRET"]?.toString() ?: "",
        )
        buildConfigField(
            FieldSpec.Type.STRING,
            "APP_VERSION",
            libs.versions.app.versionName.get(),
        )
    }

    targetConfigs("release") {
        create("android") {
            buildConfigField(FieldSpec.Type.BOOLEAN, "isDebug", "false")
        }
        create("iosArm64") {
            buildConfigField(FieldSpec.Type.BOOLEAN, "isDebug", "false")
        }
        create("iosX64") {
            buildConfigField(FieldSpec.Type.BOOLEAN, "isDebug", "false")
        }
        create("iosSimulatorArm64") {
            buildConfigField(FieldSpec.Type.BOOLEAN, "isDebug", "false")
        }
    }
}

dependencies {
    androidRuntimeClasspath(compose.uiTooling)
}

// Host unit tests (testAndroidHostTest) spin up ~30 classes' worth of in-memory SQLDelight
// DBs, Koin graphs and coroutine scopes in one fork. Give that fork a generous heap +
// metaspace so the whole suite runs in a single Gradle invocation (CI runs it that way). (TRI-69)
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    jvmArgs("-XX:+UseG1GC", "-XX:MaxMetaspaceSize=512m")
    // Recycle the test JVM each class so leaked coroutine scopes / Dispatchers.IO listeners
    // (e.g. SyncManager's uncancelled scope) can't accumulate and stall the full-suite run.
    forkEvery = 1
    // Roborazzi always records: captureRoboImage writes PNGs to build/previews/ so UI
    // changes can be reviewed without building for a device. No-op for non-preview tests.
    systemProperty("roborazzi.test.record", "true")
}
