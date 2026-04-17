package az.tribe.lifeplanner.data.network

import az.tribe.lifeplanner.data.repository.BuiltinCoachStore
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.CoachAvatar
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.model.CoachProfile
import co.touchlab.kermit.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class BuiltinCoachDto(
    val id: String,
    val name: String,
    val title: String,
    val category: String,
    val emoji: String,
    @SerialName("image_url")         val imageUrl: String? = null,
    val greeting: String,
    val bio: String,
    @SerialName("fun_fact")          val funFact: String? = null,
    val specialties: List<String> = emptyList(),
    val personality: String? = null,
    val city: String? = null,
    val timezone: String = "UTC",
    @SerialName("country_flag")      val countryFlag: String? = null,
    @SerialName("avatar_bg_color")   val avatarBgColor: String = "#6366F1",
    @SerialName("avatar_accent_color") val avatarAccentColor: String = "#818CF8",
    @SerialName("avatar_icon_name")  val avatarIconName: String = "star",
    @SerialName("xp_to_unlock")      val xpToUnlock: Int = 0,
    @SerialName("is_default_unlocked") val isDefaultUnlocked: Boolean = true,
)

private fun BuiltinCoachDto.toCoachPersona(): CoachPersona? {
    val cat = try { GoalCategory.valueOf(category) } catch (_: Exception) { return null }
    return CoachPersona(
        id = id,
        name = name,
        title = title,
        category = cat,
        emoji = emoji,
        greeting = greeting,
        specialties = specialties,
        personality = personality ?: "",
        avatar = CoachAvatar(
            backgroundColor = avatarBgColor,
            accentColor = avatarAccentColor,
            iconName = avatarIconName
        ),
        profile = CoachProfile(
            bio = bio,
            funFact = funFact ?: "",
            xpToUnlock = xpToUnlock,
            isDefaultUnlocked = isDefaultUnlocked
        ),
        timezone = timezone,
        city = city ?: "",
        countryFlag = countryFlag ?: "",
        imageUrl = imageUrl
    )
}

class BuiltinCoachFetcher(private val supabase: SupabaseClient) {

    private val log = Logger.withTag("BuiltinCoachFetcher")

    suspend fun fetch() {
        try {
            val dtos = supabase.postgrest["builtin_coaches"]
                .select {
                    filter { eq("is_active", true) }
                    order("display_order", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                .decodeList<BuiltinCoachDto>()

            val coaches = dtos.mapNotNull { it.toCoachPersona() }
            if (coaches.isNotEmpty()) {
                BuiltinCoachStore.update(coaches)
                log.i { "Loaded ${coaches.size} builtin coaches from Supabase" }
            }
        } catch (e: Exception) {
            log.w { "Failed to fetch builtin coaches (using hardcoded fallback): ${e.message}" }
        }
    }
}
