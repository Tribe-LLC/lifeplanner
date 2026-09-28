package az.tribe.lifeplanner.data.travel

import az.tribe.lifeplanner.domain.service.DayForecast
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A place found by name, for a trip's weather. */
data class Place(val name: String, val country: String?, val latitude: Double, val longitude: Double, val countryCode: String? = null)

/**
 * Where a trip is and what the weather will be, from Open-Meteo (free, keyless, same source as the
 * Today weather). The forecast reaches 16 days ahead, so a trip further out has no weather yet and
 * the page just leaves it off. Every failure is quiet: null or empty.
 */
class TripWeather(private val client: HttpClient) {

    private val forecasts = mutableMapOf<String, List<DayForecast>>()

    suspend fun find(name: String): Place? = try {
        val r: GeoResponse = client.get(GEO_URL) {
            parameter("name", name.trim())
            parameter("count", 1)
            parameter("format", "json")
        }.body()
        r.results?.firstOrNull()?.let { Place(it.name, it.country, it.latitude, it.longitude, it.countryCode) }
    } catch (e: Exception) {
        Logger.w("TripWeather") { "Geocoding failed: ${e.message}" }
        null
    }

    suspend fun forecast(latitude: Double, longitude: Double, start: LocalDate, end: LocalDate, today: LocalDate): List<DayForecast> {
        val last = today.plus(DatePeriod(days = MAX_DAYS - 1))
        if (start > last || end < today) return emptyList()
        val from = maxOf(start, today)
        val to = minOf(end, last)
        val key = "$latitude,$longitude,$from,$to"
        forecasts[key]?.let { return it }
        return try {
            val r: ForecastResponse = client.get(FORECAST_URL) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code")
                parameter("start_date", from.toString())
                parameter("end_date", to.toString())
                parameter("timezone", "auto")
            }.body()
            val d = r.daily ?: return emptyList()
            d.time.indices.mapNotNull { i ->
                val date = runCatching { LocalDate.parse(d.time[i]) }.getOrNull() ?: return@mapNotNull null
                DayForecast(
                    date = date,
                    maxC = d.max?.getOrNull(i)?.roundToInt() ?: return@mapNotNull null,
                    minC = d.min?.getOrNull(i)?.roundToInt() ?: return@mapNotNull null,
                    rainChance = d.rain?.getOrNull(i),
                    wmoCode = d.code?.getOrNull(i) ?: 0,
                )
            }.also { forecasts[key] = it }
        } catch (e: Exception) {
            Logger.w("TripWeather") { "Forecast failed: ${e.message}" }
            emptyList()
        }
    }

    /**
     * The average daytime high over days that have passed, for a trip's recap. Open-Meteo keeps
     * about three months of past days on the same forecast endpoint. Null when it cannot say.
     */
    suspend fun averageHigh(latitude: Double, longitude: Double, start: LocalDate, end: LocalDate): Int? = try {
        val r: ForecastResponse = client.get(FORECAST_URL) {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("daily", "temperature_2m_max")
            parameter("start_date", start.toString())
            parameter("end_date", end.toString())
            parameter("timezone", "auto")
        }.body()
        r.daily?.max?.filterNotNull()?.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    } catch (e: Exception) {
        Logger.w("TripWeather") { "Past weather failed: ${e.message}" }
        null
    }

    private companion object {
        const val GEO_URL = "https://geocoding-api.open-meteo.com/v1/search"
        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        const val MAX_DAYS = 16
    }
}

@Serializable
private data class GeoResponse(val results: List<GeoResult>? = null)

@Serializable
private data class GeoResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
)

@Serializable
private data class ForecastResponse(val daily: ForecastDaily? = null)

@Serializable
private data class ForecastDaily(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m_max") val max: List<Double?>? = null,
    @SerialName("temperature_2m_min") val min: List<Double?>? = null,
    @SerialName("precipitation_probability_max") val rain: List<Int?>? = null,
    @SerialName("weather_code") val code: List<Int?>? = null,
)
