package az.tribe.lifeplanner.di


import az.tribe.lifeplanner.data.network.AuthInterceptorPlugin
import az.tribe.lifeplanner.data.network.FirebaseTokenProvider
import az.tribe.lifeplanner.data.network.LifePlannerApiService
import az.tribe.lifeplanner.data.network.LifePlannerApiServiceImpl
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module



const val BASE_URL = "https://generativelanguage.googleapis.com"
const val BACKEND_BASE_URL = "https://pufpuf-app-3m2r2.ondigitalocean.app/"
const val GEMINI_PRO = "gemini-2.0-flash"

val networkModule = module {

    // Gemini API client (existing)
    factory<HttpClient>(named("gemini")) {
        createHttpClient()
    }

    // Backend API client with Firebase auth
    factory<HttpClient>(named("backend")) {
        createBackendHttpClient(get())
    }

    // Backend API service
    single<LifePlannerApiService> {
        LifePlannerApiServiceImpl(get(named("backend")))
    }

}

private fun createHttpClient(): HttpClient {
    return HttpClient {

        install(HttpTimeout) {
            socketTimeoutMillis = 60_000
            requestTimeoutMillis = 60_000
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
            logger = object : Logger {
                override fun log(message: String) {
                    co.touchlab.kermit.Logger.d(tag = "KtorClient", null) { message }
                }
            }
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
            url(BASE_URL)
            contentType(ContentType.Application.Json)
        }
    }
}

private fun createBackendHttpClient(
    tokenProvider: FirebaseTokenProvider
): HttpClient {
    return HttpClient {

        install(HttpTimeout) {
            socketTimeoutMillis = 30_000
            requestTimeoutMillis = 30_000
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
            logger = object : Logger {
                override fun log(message: String) {
                    co.touchlab.kermit.Logger.d(tag = "BackendAPI", null) { message }
                }
            }
        }

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }

        // Install auth interceptor
        install(AuthInterceptorPlugin) {
            this.tokenProvider = { tokenProvider.getIdToken(forceRefresh = false) }
        }

        defaultRequest {
            url(BACKEND_BASE_URL)
            contentType(ContentType.Application.Json)
        }
    }
}
