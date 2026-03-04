package az.tribe.lifeplanner.data.network

import az.tribe.lifeplanner.data.model.DataError
import co.touchlab.kermit.Logger
import az.tribe.lifeplanner.data.model.ErrorDto
import az.tribe.lifeplanner.data.model.Result
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext

suspend inline fun <reified T> safeCall(
    execute: () -> HttpResponse
): Result<T, DataError.Remote> {
    val response = try {
        execute()
    } catch (e: SocketTimeoutException) {
        return Result.Error(DataError.Remote.REQUEST_TIMEOUT)
    } catch (e: UnresolvedAddressException) {
        return Result.Error(DataError.Remote.NO_INTERNET)
    } catch (e: Exception) {
        Logger.e("HttpClientExt") { "Network request failed: ${e.message}" }
        coroutineContext.ensureActive()
        return Result.Error(DataError.Remote.UNKNOWN)
    }
    return responseToResult(response)
}

inline fun <T, R> Result<T, DataError.Remote>.flatMap(transform: (T) -> Result<R, DataError.Remote>): Result<R, DataError.Remote> {
    return when (this) {
        is Result.Success -> transform(this.data)
        is Result.Error -> this
    }
}

suspend inline fun <reified T> responseToResult(
    response: HttpResponse
): Result<T, DataError.Remote> {

    return when (response.status.value) {
        in 200..299 -> {
            try {
                Result.Success(response.body<T>())
            } catch (e: NoTransformationFoundException) {
                Result.Error(DataError.Remote.SERIALIZATION)
            }
        }
        401 ->  Result.Error(DataError.Remote.UNATHORIZATION)
        408 -> Result.Error(DataError.Remote.REQUEST_TIMEOUT)
        429 -> Result.Error(DataError.Remote.TOO_MANY_REQUESTS)
        in 500..599 -> Result.Error(DataError.Remote.SERVER)
        else -> {
            val data = Json.decodeFromString<ErrorDto>(response.body())
            Result.Error(DataError.Remote.VALIDATION(data.message))
        }
    }
}
