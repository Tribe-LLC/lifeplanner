package az.tribe.lifeplanner.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

actual fun getSupabaseHttpEngine(): HttpClientEngine? = Darwin.create()
