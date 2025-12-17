package az.tribe.lifeplanner.data.network

import az.tribe.lifeplanner.data.network.dto.AuthTokenRequest
import az.tribe.lifeplanner.data.network.dto.AuthTokenResponse
import az.tribe.lifeplanner.data.network.dto.BulkSyncRequest
import az.tribe.lifeplanner.data.network.dto.BulkSyncResponse
import az.tribe.lifeplanner.data.network.dto.GoalDTO
import az.tribe.lifeplanner.data.network.dto.UserProgressDTO
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Implementation of LifePlannerApiService using Ktor
 */
class LifePlannerApiServiceImpl(
    private val httpClient: HttpClient
) : LifePlannerApiService {

    companion object {
        private const val BASE_PATH = "api"
    }

    override suspend fun authenticateWithToken(request: AuthTokenRequest): AuthTokenResponse {
        return httpClient.post("$BASE_PATH/auth/token/") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun getGoals(): List<GoalDTO> {
        return httpClient.get("$BASE_PATH/goals/").body()
    }

    override suspend fun createGoal(goal: GoalDTO): GoalDTO {
        return httpClient.post("$BASE_PATH/goals/") {
            contentType(ContentType.Application.Json)
            setBody(goal)
        }.body()
    }

    override suspend fun updateGoal(id: String, goal: GoalDTO): GoalDTO {
        return httpClient.patch("$BASE_PATH/goals/$id/") {
            contentType(ContentType.Application.Json)
            setBody(goal)
        }.body()
    }

    override suspend fun deleteGoal(id: String) {
        httpClient.delete("$BASE_PATH/goals/$id/")
    }

    override suspend fun getActiveGoals(): List<GoalDTO> {
        return httpClient.get("$BASE_PATH/goals/active/").body()
    }

    override suspend fun syncGoals(request: BulkSyncRequest): BulkSyncResponse {
        return httpClient.post("$BASE_PATH/goals/bulk_sync/") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun getUserProgress(): UserProgressDTO {
        return httpClient.get("$BASE_PATH/progress/").body()
    }
}
