package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus

/**
 * What one line typed into "Add anything" turns into. [kind] null means a new routine (habit)
 * rather than a log.
 */
data class ParsedEntry(
    val area: PlanArea,
    val kind: LogKind?,
    val title: String,
    val amount: Double? = null,
    val currency: String? = null,
    val category: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val durationMin: Int? = null,
    val notes: String? = null,
    /** Set for a bill or subscription ("netflix 12 monthly"): how it repeats, and when it is next due. */
    val bill: BillRule? = null,
    val firstDue: LocalDate? = null,
) {
    val isRoutine get() = kind == null
    val isBill get() = bill != null
}

data class ParsedInput(val entries: List<ParsedEntry>, val occurredAt: LocalDateTime)

/**
 * Turns "lunch ramen 12.50" into a meal and a spend, "ran 5k in 28 min" into a workout, "drink
 * water every day" into a routine. Plain rules, no network: it runs on every keystroke and has to
 * work offline. Anything it cannot place comes back empty, and the sheet offers the coach instead.
 */
object QuickAddParser {

    private val symbols = mapOf("€" to "EUR", "$" to "USD", "£" to "GBP", "₼" to "AZN", "₺" to "TRY", "₽" to "RUB", "¥" to "JPY", "₹" to "INR")
    private val codes = mapOf(
        "eur" to "EUR", "euro" to "EUR", "euros" to "EUR", "usd" to "USD", "dollar" to "USD", "dollars" to "USD",
        "gbp" to "GBP", "azn" to "AZN", "manat" to "AZN", "try" to "TRY", "lira" to "TRY", "rub" to "RUB",
        "jpy" to "JPY", "yen" to "JPY", "inr" to "INR", "rupees" to "INR",
    )

    private val workoutWords = setOf(
        "ran", "run", "running", "jog", "jogged", "jogging", "walk", "walked", "walking", "gym", "workout", "worked",
        "lifted", "lifting", "weights", "yoga", "swim", "swam", "swimming", "cycled", "cycling", "bike", "biked",
        "ride", "rode", "hiit", "pilates", "stretch", "stretched", "stretching", "pushups", "squats", "hike", "hiked",
        "football", "tennis", "boxing", "climbing", "cardio", "training", "trained",
    )
    private val mealWords = setOf(
        "breakfast", "lunch", "dinner", "supper", "brunch", "snack", "ate", "eat", "eating", "meal", "pizza", "ramen",
        "burger", "salad", "sandwich", "pasta", "soup", "rice", "sushi", "kebab", "steak", "chicken", "eggs", "oats",
        "coffee", "latte", "cappuccino", "tea", "smoothie", "dessert", "cake",
    )
    private val studyWords = setOf(
        "studied", "study", "studying", "read", "reading", "class", "lecture", "course", "homework", "revision",
        "revised", "practice", "practiced", "learned", "learning", "lesson", "ielts", "toefl", "exam", "flashcards",
    )
    private val sleepWords = setOf("slept", "sleep", "nap", "napped")
    private val moodWords = setOf("felt", "feeling", "mood", "happy", "sad", "stressed", "anxious", "tired", "calm", "grateful", "angry")
    private val waterWords = setOf("water", "glass", "glasses")
    private val travelWords = setOf("flight", "flights", "fly", "flying", "hotel", "airbnb", "hostel", "trip", "visa", "train", "ferry")
    private val incomeWords = setOf("salary", "income", "paycheck", "earned", "refund", "bonus", "freelance", "invoice")
    private val spendWords = setOf(
        "spent", "spend", "bought", "buy", "paid", "cost", "bill", "bills", "rent", "taxi", "uber", "bolt", "bus",
        "metro", "fuel", "petrol", "gas", "groceries", "grocery", "shopping", "clothes", "shoes", "cinema", "netflix",
        "spotify", "subscription", "gift", "pharmacy", "medicine", "haircut", "phone", "internet",
    )
    private val routineMarkers = listOf("every day", "everyday", "daily", "each day", "every morning", "every night", "every evening", "each morning", "each night")

    private val badSleep = setOf("badly", "bad", "poorly", "terribly", "little", "barely")

    private val moneyBefore = Regex("""([€$£₼₺₽¥₹])\s?(\d+(?:[.,]\d{1,2})?)""")
    private val moneyAfter = Regex("""(\d+(?:[.,]\d{1,2})?)\s?(€|\$|£|₼|₺|₽|¥|₹|eur|euros?|usd|dollars?|gbp|azn|manat|try|lira|rub|jpy|yen|inr|rupees)\b""", RegexOption.IGNORE_CASE)
    private val bareDecimal = Regex("""(?<![\d:])(\d+[.,]\d{1,2})(?![\d:]|\s?(?:km|k|h|hr|hrs|hours|min|mins|minutes|kg)\b)""")
    private val bareNumber = Regex("""(?<![\d:.,])(\d{1,6})(?![\d:.,]|\s?(?:km|k|h|hr|hrs|hours|min|mins|minutes|m|kg|glass|glasses|x|times|am|pm)\b)""", RegexOption.IGNORE_CASE)
    private val minutes = Regex("""(\d+)\s?(?:min|mins|minutes|m)\b""", RegexOption.IGNORE_CASE)
    private val hours = Regex("""(\d+(?:[.,]\d)?)\s?(?:h|hr|hrs|hour|hours)\b""", RegexOption.IGNORE_CASE)
    private val hoursAndMin = Regex("""(\d+)\s?h\s?(\d{1,2})\s?m?\b""", RegexOption.IGNORE_CASE)
    private val clock = Regex("""\b(\d{1,2}):(\d{2})\b(?!\s?(?:am|pm))""")
    private val atTime = Regex("""\bat\s(\d{1,2})(?::(\d{2}))?\s?(am|pm)?\b""", RegexOption.IGNORE_CASE)
    private val distance = Regex("""(\d+(?:[.,]\d+)?)\s?(?:k|km)\b""", RegexOption.IGNORE_CASE)
    private val glasses = Regex("""(\d+)\s?(?:glass|glasses|cups?)\b""", RegexOption.IGNORE_CASE)

    fun parse(raw: String, now: LocalDateTime, defaultCurrency: String): ParsedInput {
        val text = raw.trim()
        if (text.isEmpty()) return ParsedInput(emptyList(), now)
        val lower = text.lowercase()
        val words = lower.split(Regex("""[^\p{L}\p{N}']+""")).filter { it.isNotBlank() }.toSet()

        // When: "yesterday", "at 8", "at 13:10".
        var at = now
        if ("yesterday" in words) at = LocalDateTime(now.date.minus(DatePeriod(days = 1)), now.time)
        atTime.find(lower)?.let { m ->
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toIntOrNull() ?: 0
            when (m.groupValues[3].lowercase()) {
                "pm" -> if (h < 12) h += 12
                "am" -> if (h == 12) h = 0
            }
            if (h in 0..23 && min in 0..59) at = LocalDateTime(at.date, LocalTime(h, min))
        }

        // A bill or subscription: an amount that repeats, "rent 600 every month on the 1st".
        parseBill(text, lower, words, at.date, defaultCurrency)?.let { return ParsedInput(listOf(it), at) }

        // Routine first: "drink water every day" is a habit, not a log of water.
        routineMarkers.firstOrNull { it in lower }?.let { marker ->
            val title = text.replace(Regex(Regex.escape(marker), RegexOption.IGNORE_CASE), "").trim().trimEnd(',', '.').ifBlank { text }
            return ParsedInput(listOf(ParsedEntry(areaForRoutine(words), null, title.capitalizeFirst())), at)
        }

        val money = findMoney(text, lower, words, defaultCurrency)
        val duration = findDuration(lower)
        val km = distance.find(lower)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val cleanTitle = titleFrom(text)

        val out = mutableListOf<ParsedEntry>()
        val isWorkout = words.any { it in workoutWords } || km != null && words.none { it in travelWords }
        val isMeal = words.any { it in mealWords }
        val isStudy = words.any { it in studyWords } && !isWorkout
        val isSleep = words.any { it in sleepWords }
        val isMood = words.any { it in moodWords } && !isSleep
        val isWater = words.any { it in waterWords } && !isMeal
        val isTravel = words.any { it in travelWords }
        val isIncome = words.any { it in incomeWords }

        when {
            isSleep -> {
                val h = hoursAndMin.find(lower)?.let { it.groupValues[1].toInt() + it.groupValues[2].toInt() / 60.0 }
                    ?: hours.find(lower)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
                val bad = words.any { it in badSleep }
                out += ParsedEntry(
                    PlanArea.MIND, LogKind.SLEEP,
                    title = when {
                        h != null -> "Slept ${formatHours(h)}"
                        bad -> "Slept badly"
                        else -> "Slept well".takeIf { "well" in words || "great" in words } ?: "Sleep"
                    },
                    quantity = h, unit = h?.let { "h" },
                    notes = if (bad) "badly" else null,
                )
            }
            isWorkout -> out += ParsedEntry(
                PlanArea.FITNESS, LogKind.WORKOUT,
                title = workoutTitle(words, km, cleanTitle),
                quantity = km, unit = km?.let { "km" }, durationMin = duration,
            )
            isStudy -> out += ParsedEntry(PlanArea.STUDY, LogKind.STUDY, title = studyTitle(cleanTitle), durationMin = duration)
            isWater -> {
                val n = glasses.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                out += ParsedEntry(PlanArea.HABITS, LogKind.WATER, title = if (n == 1) "Glass of water" else "$n glasses of water", quantity = n.toDouble(), unit = "glass")
            }
            isMood -> out += ParsedEntry(PlanArea.MIND, LogKind.MOOD, title = cleanTitle)
        }
        if (isMeal) out += ParsedEntry(PlanArea.MEALS, LogKind.MEAL, title = cleanTitle.let { mealTitle(it, words) })
        if (isTravel && money == null) out += ParsedEntry(PlanArea.TRAVEL, LogKind.NOTE, title = cleanTitle)

        if (money != null) {
            val (amount, currency) = money
            out += when {
                isIncome -> ParsedEntry(PlanArea.MONEY, LogKind.INCOME, title = cleanTitle, amount = amount, currency = currency, category = "income")
                else -> ParsedEntry(
                    PlanArea.MONEY, LogKind.EXPENSE, title = cleanTitle, amount = amount, currency = currency,
                    category = spendCategory(words, isMeal, isTravel),
                )
            }
        } else if (out.isEmpty() && words.any { it in spendWords }) {
            // "paid rent" with no amount: nothing to count yet, so nothing to file.
            return ParsedInput(emptyList(), at)
        }
        return ParsedInput(out, at)
    }

    private val billMarkers = listOf(
        Regex("""\b(?:every|each|per|a)\s+month\b|\bmonthly\b|/\s?mo(?:nth)?\b""") to BillRepeat.MONTHLY,
        Regex("""\b(?:every|each|per|a)\s+week\b|\bweekly\b|/\s?w(?:ee)?k\b""") to BillRepeat.WEEKLY,
        Regex("""\b(?:every|each|per|a)\s+year\b|\byearly\b|\bannually\b|/\s?y(?:ea)?r\b""") to BillRepeat.YEARLY,
    )
    private val dayOfMonth = Regex("""\bon\s+(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?\b""", RegexOption.IGNORE_CASE)
    private val weekday = Regex("""\bon\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)s?\b""", RegexOption.IGNORE_CASE)
    private val notBills = setOf("save", "saving", "savings", "invest", "investing")

    /** "netflix 12 monthly", "rent 600 every month on the 1st", "gym 30 every week on monday". */
    private fun parseBill(text: String, lower: String, words: Set<String>, today: LocalDate, defaultCurrency: String): ParsedEntry? {
        val (marker, repeat) = billMarkers.firstNotNullOfOrNull { (re, r) -> re.find(lower)?.let { it to r } } ?: return null
        if (words.any { it in incomeWords || it in notBills }) return null
        val day = dayOfMonth.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..31 }
        val wd = weekday.find(lower)?.groupValues?.get(1)?.let { w -> DayOfWeek.entries.firstOrNull { it.name.equals(w, ignoreCase = true) } }
        val rest = text.replace(dayOfMonth, " ").replace(weekday, " ").replace(atTime, " ")
            .replace(Regex(Regex.escape(marker.value), RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s{2,}"""), " ").trim()
        // The repeat word makes a bare number money, as "bill" would: "gym 30 every week".
        val (amount, currency) = findMoney(rest, rest.lowercase(), words + "bill", defaultCurrency) ?: return null
        val title = titleFrom(rest)
            .replace(Regex("""(?<![\d.,])\d{1,6}(?![\d.,])"""), " ")
            .replace(Regex("""\b(every|each|per|for|on|the|a|an)\s*$""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s{2,}"""), " ").trim().trim(',', '.', '-')
            .ifBlank { "Bill" }.capitalizeFirst()
        val due = Bills.firstDue(repeat, today, day, wd)
        val rule = when (repeat) {
            BillRepeat.MONTHLY -> BillRule(repeat, day = day ?: due.day)
            else -> BillRule.startingOn(repeat, due)
        }
        val category = spendCategory(words, isMeal = false, isTravel = false).let { if (it == "other") Bills.CATEGORY else it }
        return ParsedEntry(PlanArea.MONEY, LogKind.EXPENSE, title = title, amount = amount, currency = currency, category = category, bill = rule, firstDue = due)
    }

    fun spendCategories() = listOf("food", "transport", "bills", "shopping", "fun", "travel", "health", "other")

    private fun spendCategory(words: Set<String>, isMeal: Boolean, isTravel: Boolean): String = when {
        isTravel -> "travel"
        isMeal || words.any { it in setOf("groceries", "grocery", "supermarket", "restaurant", "cafe", "bar") } -> "food"
        words.any { it in setOf("taxi", "uber", "bolt", "bus", "metro", "fuel", "petrol", "gas", "parking", "ticket") } -> "transport"
        words.any { it in setOf("rent", "bill", "bills", "electricity", "internet", "phone", "subscription", "netflix", "spotify") } -> "bills"
        words.any { it in setOf("clothes", "shoes", "shopping", "bought", "amazon") } -> "shopping"
        words.any { it in setOf("cinema", "movie", "concert", "game", "games", "party") } -> "fun"
        words.any { it in setOf("pharmacy", "medicine", "doctor", "dentist", "gym") } -> "health"
        else -> "other"
    }

    private fun findMoney(text: String, lower: String, words: Set<String>, defaultCurrency: String): Pair<Double, String>? {
        moneyBefore.find(text)?.let { m -> return m.groupValues[2].toAmount() to (symbols[m.groupValues[1]] ?: defaultCurrency) }
        moneyAfter.find(text)?.let { m ->
            val unit = m.groupValues[2].lowercase()
            return m.groupValues[1].toAmount() to (symbols[unit] ?: codes[unit] ?: defaultCurrency)
        }
        bareDecimal.find(lower)?.let { return it.groupValues[1].toAmount() to defaultCurrency }
        // A plain whole number only counts as money next to a spending word: "5 glasses" is not €5.
        val moneyish = words.any { it in spendWords || it in incomeWords || it in mealWords || it in travelWords }
        if (moneyish) bareNumber.find(lower)?.let { return it.groupValues[1].toAmount() to defaultCurrency }
        return null
    }

    private fun findDuration(lower: String): Int? {
        hoursAndMin.find(lower)?.let { return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
        clock.find(lower)?.takeIf { !lower.contains("at ${it.value}") }?.let { m ->
            // "28:10" after a run is a time taken, in minutes and seconds.
            return m.groupValues[1].toInt() + if (m.groupValues[2].toInt() >= 30) 1 else 0
        }
        val h = hours.find(lower)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val m = minutes.find(lower)?.groupValues?.get(1)?.toIntOrNull()
        if (h == null && m == null) return null
        return ((h ?: 0.0) * 60).toInt() + (m ?: 0)
    }

    private fun titleFrom(text: String): String {
        val stripped = text
            .replace(moneyBefore, "")
            .replace(moneyAfter, "")
            .replace(Regex("""(?<![\d:])\d+[.,]\d{1,2}(?![\d:])"""), "")
            .replace(atTime, "")
            .replace(Regex("""\byesterday\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(for|on|at|in)\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim().trim(',', '.', '-')
        return stripped.ifBlank { text }.capitalizeFirst()
    }

    /**
     * "Studied biology 45 min" files as "Biology", so the Study page can add up time per subject.
     * With nothing left after the study words and the time, it is just "Study".
     */
    private fun studyTitle(title: String): String {
        val verbs = Regex("""\b(studied|studying|study|revised|revising|revision|did|some|of|for)\b""", RegexOption.IGNORE_CASE)
        val rest = title.replace(hoursAndMin, " ").replace(hours, " ").replace(minutes, " ").replace(verbs, " ")
            .replace(Regex("""\s{2,}"""), " ").trim().trim(',', '.', '-')
        return rest.ifBlank { "Study" }.capitalizeFirst()
    }

    private fun mealTitle(title: String, words: Set<String>): String {
        val slot = listOf("breakfast", "brunch", "lunch", "dinner", "supper", "snack").firstOrNull { it in words } ?: return title
        val rest = title.split(' ').filter { it.lowercase() != slot }.joinToString(" ").trim()
        return if (rest.isBlank()) slot.capitalizeFirst() else "${rest.capitalizeFirst()}, $slot"
    }

    private fun workoutTitle(words: Set<String>, km: Double?, fallback: String): String {
        val activity = when {
            words.any { it in setOf("ran", "run", "running", "jog", "jogged", "jogging") } -> "Run"
            words.any { it in setOf("walk", "walked", "walking") } -> "Walk"
            words.any { it in setOf("swim", "swam", "swimming") } -> "Swim"
            words.any { it in setOf("cycled", "cycling", "bike", "biked", "ride", "rode") } -> "Ride"
            words.any { it in setOf("hike", "hiked") } -> "Hike"
            words.any { it == "yoga" } -> "Yoga"
            else -> return fallback
        }
        return if (km != null) "$activity, ${formatNumber(km)} km" else activity
    }

    private fun areaForRoutine(words: Set<String>): PlanArea = when {
        words.any { it in workoutWords } -> PlanArea.FITNESS
        words.any { it in studyWords } -> PlanArea.STUDY
        words.any { it in sleepWords || it in setOf("meditate", "meditation", "journal", "breathe") } -> PlanArea.MIND
        words.any { it in setOf("save", "saving", "budget") } -> PlanArea.MONEY
        else -> PlanArea.HABITS
    }

    private fun String.toAmount() = replace(',', '.').toDouble()
    private fun String.capitalizeFirst() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    private fun formatNumber(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else ((d * 10).toInt() / 10.0).toString()
    private fun formatHours(h: Double): String {
        val total = (h * 60).toInt()
        return if (total % 60 == 0) "${total / 60}h" else "${total / 60}h ${(total % 60).toString().padStart(2, '0')}m"
    }
}
