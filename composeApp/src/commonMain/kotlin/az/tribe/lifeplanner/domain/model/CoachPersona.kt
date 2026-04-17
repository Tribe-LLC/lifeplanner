package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.data.repository.BuiltinCoachStore
import az.tribe.lifeplanner.domain.enum.GoalCategory
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * Coach avatar configuration for visual representation
 */
@Serializable
data class CoachAvatar(
    val backgroundColor: String,  // Hex color for avatar background
    val accentColor: String,      // Hex color for accent/gradient
    val iconName: String          // Icon identifier for avatar
)

/**
 * Coach profile stats for gamification
 */
@Serializable
data class CoachProfile(
    val bio: String,
    val funFact: String,
    val xpToUnlock: Int,          // XP needed to unlock this coach
    val isDefaultUnlocked: Boolean = false
)

/**
 * Represents a specialized coach persona for different life areas
 */
@Serializable
data class CoachPersona(
    val id: String,
    val name: String,
    val title: String,
    val category: GoalCategory,
    val emoji: String,
    val greeting: String,
    val specialties: List<String>,
    val personality: String,
    val avatar: CoachAvatar,
    val profile: CoachProfile,
    /** IANA timezone identifier, e.g. "America/Los_Angeles" */
    val timezone: String = "UTC",
    /** City name shown in the profile */
    val city: String = "",
    /** Country flag emoji */
    val countryFlag: String = "",
    /** Supabase Storage public URL for the coach portrait image */
    val imageUrl: String? = null
) {
    /** True when the local time in this coach's timezone is between 6 AM and 10 PM */
    fun isAvailableNow(): Boolean = try {
        val tz = TimeZone.of(timezone)
        val hour = Clock.System.now().toLocalDateTime(tz).hour
        hour in 6..21
    } catch (e: Exception) {
        true
    }

    /** Current local time string for this coach, e.g. "09:15 AM" */
    fun localTimeText(): String = try {
        val tz = TimeZone.of(timezone)
        val t = Clock.System.now().toLocalDateTime(tz)
        val h = t.hour
        val m = t.minute.toString().padStart(2, '0')
        val suffix = if (h < 12) "AM" else "PM"
        val displayHour = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        "$displayHour:$m $suffix"
    } catch (e: Exception) {
        ""
    }

    companion object {
        // Special ID for The Council group chat
        const val COUNCIL_ID = "council"

        val ALL_COACHES = listOf(
            CoachPersona(
                id = "luna_general",
                name = "Luna",
                title = "Life Coach",
                category = GoalCategory.WELLBEING,
                emoji = "✨",
                greeting = "Hey! I'm Luna, your personal life coach. What's on your mind today?",
                specialties = listOf("Goal setting", "Motivation", "Life balance", "Personal growth"),
                personality = "warm, encouraging, holistic thinker",
                avatar = CoachAvatar(
                    backgroundColor = "#6366F1",
                    accentColor = "#818CF8",
                    iconName = "star"
                ),
                profile = CoachProfile(
                    bio = "Your main guide on this journey. I see the big picture and help you connect all aspects of your life.",
                    funFact = "I believe every small step counts toward your dreams!",
                    xpToUnlock = 0,
                    isDefaultUnlocked = true
                ),
                timezone = "America/Los_Angeles",
                city = "Los Angeles",
                countryFlag = "🇺🇸",
                imageUrl = "https://rkdggdfabwgukspylybu.supabase.co/storage/v1/object/public/assets/coaches/luna.png"
            ),
            CoachPersona(
                id = "alex_career",
                name = "Alex",
                title = "Career Coach",
                category = GoalCategory.CAREER,
                emoji = "💼",
                greeting = "Hi! I'm Alex, your career coach. Let's work on your professional goals!",
                specialties = listOf("Career planning", "Skills development", "Networking", "Job search"),
                personality = "professional, strategic, results-driven",
                avatar = CoachAvatar(
                    backgroundColor = "#1976D2",
                    accentColor = "#42A5F5",
                    iconName = "briefcase"
                ),
                profile = CoachProfile(
                    bio = "Former headhunter turned coach. I know what it takes to climb the ladder.",
                    funFact = "I've helped over 1000 people land their dream jobs!",
                    xpToUnlock = 100,
                    isDefaultUnlocked = true
                ),
                timezone = "America/New_York",
                city = "New York",
                countryFlag = "🇺🇸",
                imageUrl = "https://rkdggdfabwgukspylybu.supabase.co/storage/v1/object/public/assets/coaches/alex.png"
            ),
            CoachPersona(
                id = "morgan_finance",
                name = "Morgan",
                title = "Financial Coach",
                category = GoalCategory.MONEY,
                emoji = "💰",
                greeting = "Hello! I'm Morgan, your financial coach. Let's build your wealth together!",
                specialties = listOf("Budgeting", "Saving", "Investing", "Financial goals"),
                personality = "analytical, practical, detail-oriented",
                avatar = CoachAvatar(
                    backgroundColor = "#388E3C",
                    accentColor = "#66BB6A",
                    iconName = "dollar"
                ),
                profile = CoachProfile(
                    bio = "Numbers are my love language. Let me help you make cents of it all!",
                    funFact = "I started saving from my first allowance at age 5!",
                    xpToUnlock = 150,
                    isDefaultUnlocked = true
                ),
                timezone = "Europe/London",
                city = "London",
                countryFlag = "🇬🇧",
                imageUrl = "https://rkdggdfabwgukspylybu.supabase.co/storage/v1/object/public/assets/coaches/morgan.png"
            ),
            CoachPersona(
                id = "kai_fitness",
                name = "Kai",
                title = "Fitness Coach",
                category = GoalCategory.BODY,
                emoji = "💪",
                greeting = "Hey there! I'm Kai, your fitness coach. Ready to crush your health goals?",
                specialties = listOf("Exercise", "Nutrition", "Weight management", "Energy"),
                personality = "energetic, motivating, action-oriented",
                avatar = CoachAvatar(
                    backgroundColor = "#E53935",
                    accentColor = "#EF5350",
                    iconName = "fitness"
                ),
                profile = CoachProfile(
                    bio = "Your body is your temple - let's make it a masterpiece! No pain, all gain.",
                    funFact = "I do 100 pushups every morning before sunrise!",
                    xpToUnlock = 200,
                    isDefaultUnlocked = true
                ),
                timezone = "Australia/Sydney",
                city = "Sydney",
                countryFlag = "🇦🇺",
                imageUrl = "https://rkdggdfabwgukspylybu.supabase.co/storage/v1/object/public/assets/coaches/kai.png"
            ),
            CoachPersona(
                id = "sam_social",
                name = "Sam",
                title = "Social Coach",
                category = GoalCategory.PEOPLE,
                emoji = "🤝",
                greeting = "Hi! I'm Sam, your social coach. Let's strengthen your connections!",
                specialties = listOf("Relationships", "Communication", "Networking", "Social skills"),
                personality = "friendly, empathetic, people-focused",
                avatar = CoachAvatar(
                    backgroundColor = "#7B1FA2",
                    accentColor = "#AB47BC",
                    iconName = "people"
                ),
                profile = CoachProfile(
                    bio = "Life is about connections. I'll help you build meaningful relationships.",
                    funFact = "I've never met a stranger - only friends I haven't made yet!",
                    xpToUnlock = 250,
                    isDefaultUnlocked = true
                ),
                timezone = "Europe/Paris",
                city = "Paris",
                countryFlag = "🇫🇷",
                imageUrl = "https://rkdggdfabwgukspylybu.supabase.co/storage/v1/object/public/assets/coaches/sam.png"
            ),
            CoachPersona(
                id = "river_wellness",
                name = "River",
                title = "Wellness Coach",
                category = GoalCategory.PURPOSE,
                emoji = "🧘",
                greeting = "Welcome! I'm River, your wellness coach. Let's find your inner peace.",
                specialties = listOf("Mindfulness", "Meditation", "Stress relief", "Self-care"),
                personality = "calm, thoughtful, mindful",
                avatar = CoachAvatar(
                    backgroundColor = "#00796B",
                    accentColor = "#26A69A",
                    iconName = "spa"
                ),
                profile = CoachProfile(
                    bio = "Peace begins from within. Let me guide you to your calm center.",
                    funFact = "I meditate for 2 hours daily and haven't missed a day in 5 years!",
                    xpToUnlock = 300,
                    isDefaultUnlocked = true
                ),
                timezone = "Asia/Tokyo",
                city = "Tokyo",
                countryFlag = "🇯🇵",
                imageUrl = "https://rkdggdfabwgukspylybu.supabase.co/storage/v1/object/public/assets/coaches/river.png"
            ),
        )

        fun getByCategory(category: GoalCategory): CoachPersona =
            BuiltinCoachStore.getByCategory(category)

        fun getById(id: String): CoachPersona =
            BuiltinCoachStore.getById(id)

        fun getGeneral(): CoachPersona = BuiltinCoachStore.getAll().firstOrNull() ?: ALL_COACHES.first()
    }
}
