package az.tribe.lifeplanner.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import az.tribe.lifeplanner.BuildKonfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val networkModule = module {

    single<HttpClient> {
        HttpClient {
            install(HttpTimeout) {
                socketTimeoutMillis = 60_000
                requestTimeoutMillis = 60_000
            }

            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        co.touchlab.kermit.Logger.d(tag = "KtorClient", null) { message }
                    }
                }
                level = if (BuildKonfig.isDebug) LogLevel.ALL else LogLevel.NONE
            }

            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                    explicitNulls = false
                })
            }

            install(WebSockets)

            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }
    }
}
