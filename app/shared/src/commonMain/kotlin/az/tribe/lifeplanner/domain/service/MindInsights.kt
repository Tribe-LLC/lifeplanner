package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Mood check-ins: a level from 1 (awful) to 5 (great) in [LifeLog.quantity], what is part of it
 * in [LifeLog.category] ("Work, Sleep"), and feeling words plus a note in [LifeLog.notes].
 * Quick add's "feeling tired" rows have no level, so one is read from their words.
 */
object MindCheckIns {
    val LEVELS = listOf("Awful", "Low", "Okay", "Good", "Great")
    val TAGS = listOf("Sleep", "Work", "Study", "Family", "Friends", "Partner", "Exercise", "Food", "Health", "Money", "Weather", "Time alone")
    val FEELINGS = listOf("Calm", "Happy", "Grateful", "Hopeful", "Proud", "Tired", "Anxious", "Stressed", "Sad", "Angry", "Lonely", "Overwhelmed")

    /** Life log category for a breathing session under Mind. */
    const val MINDFUL = "mindful"

    private const val FEEL = "feel: "

    fun label(score: Int) = LEVELS[(score - 1).coerceIn(0, 4)]

    /** -1 to 1, the way HealthKit's State of Mind wants it. */
    fun valence(score: Int) = (score.coerceIn(1, 5) - 3) / 2.0

    fun mood(score: Int): Mood = Mood.fromScore(score.coerceIn(1, 5))

    fun isCheckIn(l: LifeLog) = l.area == PlanArea.MIND && l.kind == LogKind.MOOD

    fun isMindful(l: LifeLog) = l.area == PlanArea.MIND && l.category == MINDFUL

    fun encode(feelings: List<String>, note: String?): String? {
        val lines = listOfNotNull(
            feelings.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = FEEL),
            note?.trim()?.takeIf { it.isNotEmpty() },
        )
        return lines.joinToString("\n").ifEmpty { null }
    }

    fun feelings(l: LifeLog): List<String> =
        l.notes?.lineSequence()?.firstOrNull { it.startsWith(FEEL) }?.removePrefix(FEEL)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    fun note(l: LifeLog): String? = l.notes?.lineSequence()?.filterNot { it.startsWith(FEEL) }?.joinToString("\n")?.trim()?.ifEmpty { null }

    fun tags(l: LifeLog): List<String> = l.category?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private val low = listOf("awful", "terrible", "sad", "down", "depressed", "anxious", "stressed", "angry", "upset", "lonely", "bad", "exhausted", "overwhelmed")
    private val meh = listOf("tired", "meh", "okay", "ok", "fine", "bored", "so-so")
    private val good = listOf("good", "happy", "calm", "grateful", "relaxed", "proud", "content")
    private val great = listOf("great", "amazing", "fantastic", "excited", "awesome", "wonderful")

    /** The level of a check-in, read from its words when it was typed in quick add. */
    fun score(l: LifeLog): Int? {
        l.quantity?.roundToInt()?.takeIf { it in 1..5 }?.let { return it }
        val words = l.title.lowercase().split(Regex("[^a-z-]+")).toSet()
        return when {
            great.any { it in words } -> 5
            good.any { it in words } -> 4
            low.any { it in words } -> 2
            meh.any { it in words } -> 3
            else -> null
        }
    }
}

/**
 * What goes with better or worse days, worked out on the phone from the user's own check-ins.
 * Nothing leaves the device and nothing is guessed: a factor shows only with enough days on both
 * sides, and only when the difference is large enough to mean something.
 */
object MindInsights {
    const val MIN_CHECK_IN_DAYS = 8
    private const val MIN_SIDE = 3
    private const val MIN_DELTA = 0.3

    data class Lift(val what: String, val delta: Double, val daysWith: Int)

    /** Each day's average level, from check-ins and journal entries together. */
    fun dailyMood(checkIns: List<LifeLog>, journal: List<Pair<LocalDate, Int>>): Map<LocalDate, Double> {
        val scores = checkIns.mapNotNull { l -> MindCheckIns.score(l)?.let { l.date to it } } + journal
        return scores.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.average() }
    }

    /**
     * Factors by name, each the set of days it applied. Returns the strongest few, best first:
     * lifts (positive) before drains, each by size.
     */
    fun lifts(mood: Map<LocalDate, Double>, factors: Map<String, Set<LocalDate>>, max: Int = 3): List<Lift> {
        if (mood.size < MIN_CHECK_IN_DAYS) return emptyList()
        return factors.mapNotNull { (what, days) ->
            val with = mood.filterKeys { it in days }.values
            val without = mood.filterKeys { it !in days }.values
            if (with.size < MIN_SIDE || without.size < MIN_SIDE) return@mapNotNull null
            val delta = with.average() - without.average()
            if (abs(delta) < MIN_DELTA) null else Lift(what, delta, with.size)
        }.sortedWith(compareBy({ it.delta < 0 }, { -abs(it.delta) })).take(max)
    }

    /** One decimal, with a real minus sign: "+0.8", "−0.5". */
    fun formatDelta(d: Double): String {
        val tenths = (abs(d) * 10).roundToInt()
        return (if (d < 0) "−" else "+") + "${tenths / 10}.${tenths % 10}"
    }

    /** Three low check-ins in a row, newest last: time to put the help card first. */
    fun lowRun(scoresOldestFirst: List<Int>): Boolean = scoresOldestFirst.size >= 3 && scoresOldestFirst.takeLast(3).all { it <= 2 }

    /** A short word for a stretch of days. */
    fun summary(avg: Double?): String = when {
        avg == null -> "No check-ins yet"
        avg >= 3.8 -> "Mostly good"
        avg >= 2.8 -> "Steady"
        else -> "A harder stretch"
    }

    val PROMPTS = listOf(
        "What took more energy than it should have this week?",
        "What is one thing you are looking forward to?",
        "When did you feel most like yourself today?",
        "What would make tomorrow a little easier?",
        "What is on your mind that you have not said out loud?",
        "Who made your day better lately, and did they know?",
        "What did you handle well today?",
        "What are you carrying that you could put down?",
        "What small thing went right today?",
        "If a friend had your week, what would you tell them?",
        "What do you need more of right now?",
        "What drained you today, and what filled you back up?",
        "What are you proud of this month?",
        "What is one worry you can let go of tonight?",
    )

    /** The day's question, moving on each day and on each "another question". */
    fun prompt(day: LocalDate, offset: Int): String = PROMPTS[((day.toEpochDays() + offset) % PROMPTS.size).toInt().let { if (it < 0) it + PROMPTS.size else it }]
}
