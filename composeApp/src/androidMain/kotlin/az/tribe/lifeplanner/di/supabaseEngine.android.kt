package az.tribe.lifeplanner.di

import io.ktor.client.engine.HttpClientEngine

actual fun getSupabaseHttpEngine(): HttpClientEngine? = null // Android uses default (OkHttp/CIO) which supports TLS
