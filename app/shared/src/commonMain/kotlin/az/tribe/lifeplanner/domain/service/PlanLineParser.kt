package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.domain.model.PlanArea
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * One line, read: what the plan is, by when, and how much. [target] is null when no date was said,
 * so the sheet can say "No date, so 3 months" and suggest one.
 */
data class PlanLine(
    val raw: String,
    val title: String,
    val target: LocalDate? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val km: Double? = null,
    val kg: Double? = null,
    val count: Int? = null,
    /** "for 30 days", "30 days in a row". */
    val days: Int? = null,
    /** What it is about: "Japan", "Spanish", "Meditate". */
    val subject: String? = null,
    val area: PlanArea? = null,
    /** A [PlanTemplates] id, or null when nothing matched. */
    val template: String? = null,
)

/** "put aside 100 for Japan", "paid off 200": money for a plan, not a spend. */
data class PutAside(val amount: Double, val currency: String?, val subject: String?, val paidOff: Boolean)

/**
 * Reads a plan out of one line: "Run a 5K by December", "Save 2000 for Japan by March", "Learn
 * Spanish basics". Pulls out the date, amount, distance or count, guesses the area and matches a
 * ready template. Plain rules, no network, so it runs on every keystroke.
 */
object PlanLineParser {

    private val months = mapOf(
        "january" to Month.JANUARY, "jan" to Month.JANUARY, "february" to Month.FEBRUARY, "feb" to Month.FEBRUARY,
        "march" to Month.MARCH, "mar" to Month.MARCH, "april" to Month.APRIL, "apr" to Month.APRIL, "may" to Month.MAY,
        "june" to Month.JUNE, "jun" to Month.JUNE, "july" to Month.JULY, "jul" to Month.JULY, "august" to Month.AUGUST,
        "aug" to Month.AUGUST, "september" to Month.SEPTEMBER, "sept" to Month.SEPTEMBER, "sep" to Month.SEPTEMBER,
        "october" to Month.OCTOBER, "oct" to Month.OCTOBER, "november" to Month.NOVEMBER, "nov" to Month.NOVEMBER,
        "december" to Month.DECEMBER, "dec" to Month.DECEMBER,
    )
    private const val M = "(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec)"
    private const val BY = "(?:by|before|until|till|in|for)"
    private val numberWords = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "couple of" to 2, "few" to 3,
    )

    private val endOf = Regex("""\b(?:by\s+|before\s+)?(?:the\s+)?end\s+of\s+(?:the\s+)?(year|month|$M)\b""")
    private val dayMonth = Regex("""\b(?:by|before|until|till|on)\s+(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?$M\b(?:\s+(\d{4}))?""")
    private val monthDay = Regex("""\b(?:by|before|until|till|on)\s+$M\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:,?\s+(\d{4}))?""")
    private val monthOnly = Regex("""\b$BY\s+$M\b(?:\s+(\d{4}))?""")
    private val relative = Regex("""\b(?:in|within)\s+(\d{1,3}|a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|a couple of|a few)\s+(day|week|month|year)s?\b""")
    private val season = Regex("""\b(?:by|before|until|till|in|this|next)\s+(?:the\s+)?(summer|spring|autumn|fall|winter|christmas|new year)\b""")
    private val thisNext = Regex("""\b(?:by\s+|before\s+|in\s+)?(this|next)\s+(year|month|week)\b""")
    private val year = Regex("""\b(?:by|before|in)\s+(20\d\d)\b""")

    private val symbols = mapOf("€" to "EUR", "$" to "USD", "£" to "GBP", "₼" to "AZN", "₺" to "TRY", "₽" to "RUB", "¥" to "JPY", "₹" to "INR")
    private val codes = mapOf(
        "eur" to "EUR", "euro" to "EUR", "euros" to "EUR", "usd" to "USD", "dollar" to "USD", "dollars" to "USD",
        "gbp" to "GBP", "pounds" to "GBP", "azn" to "AZN", "manat" to "AZN", "try" to "TRY", "lira" to "TRY",
        "rub" to "RUB", "jpy" to "JPY", "yen" to "JPY", "inr" to "INR", "rupees" to "INR",
    )
    private val moneyBefore = Regex("""([€$£₼₺₽¥₹])\s?(\d[\d,]*(?:\.\d{1,2})?)(k)?\b""", RegexOption.IGNORE_CASE)
    private val moneyAfter = Regex("""(\d[\d,]*(?:\.\d{1,2})?)(k)?\s?(€|\$|£|₼|₺|₽|¥|₹|eur|euros?|usd|dollars?|gbp|pounds|azn|manat|lira|rub|jpy|yen|inr|rupees)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val bareMoney = Regex("""(?<![\d.,])(\d[\d,]*(?:\.\d{1,2})?)(k)?(?![\d.,]|\s?(?:km|kg|min|h\b|days?|weeks?|months?))""", RegexOption.IGNORE_CASE)
    private val distance = Regex("""(\d+(?:[.,]\d+)?)\s?(k|km|kms)\b""", RegexOption.IGNORE_CASE)
    private val weightKg = Regex("""(\d+(?:[.,]\d+)?)\s?(kg|kgs|kilos?|kilograms?)\b""", RegexOption.IGNORE_CASE)
    private const val THINGS = "(books?|recipes?|dishes?|dinners?|meals?|push-?ups|pull-?ups|squats|sit-?ups|burpees)"
    private val counted = Regex("""\b(\d{1,4})\s+(?:new\s+)?$THINGS\b""", RegexOption.IGNORE_CASE)
    private val forDays = Regex("""\b(?:for\s+(\d{1,3})\s+days?|(\d{1,3})\s+days?\s+in\s+a\s+row|for\s+a\s+(month|week))\b""", RegexOption.IGNORE_CASE)

    private val lead = Regex("""^(?:i\s+want\s+to|i'd\s+like\s+to|i\s+would\s+like\s+to|i\s+will|i'll|i\s+plan\s+to|plan\s+to|want\s+to|my\s+goal\s+is\s+to|goal:|to)\s+""", RegexOption.IGNORE_CASE)

    fun parse(raw: String, today: LocalDate, currency: String, preset: PlanArea? = null): PlanLine {
        val text = raw.trim().replace(Regex("""\s+"""), " ")
        if (text.isEmpty()) return PlanLine(raw, "", area = preset)
        val date = findDate(text.lowercase(), today)
        // The date phrase is the same length in lower case, so it can be cut from the original.
        val rest = (date?.second?.let { r -> text.removeRange(r) } ?: text).replace(lead, "").let(::tidy)
        val lower = rest.lowercase()

        val km = when {
            Regex("""\bhalf[\s-]marathon\b""").containsMatchIn(lower) -> 21.1
            Regex("""\bmarathon\b""").containsMatchIn(lower) -> 42.2
            else -> distance.find(lower)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        }
        val kg = weightKg.find(lower)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val count = counted.find(lower)?.groupValues?.get(1)?.toIntOrNull()
        val days = forDays.find(lower)?.let { m ->
            m.groupValues[1].toIntOrNull() ?: m.groupValues[2].toIntOrNull() ?: if (m.groupValues[3] == "week") 7 else 30
        }

        val template = templateFor(lower, km, kg, count, days)
        val money = if (template == PlanTemplates.SAVE || template == PlanTemplates.DEBT) findMoney(rest, currency) else null
        val natural = template?.let(::areaOf)
        val area = when {
            template in STRONG -> natural
            // "Meditate for 30 days" is Mind and "No sugar for 30 days" Meals: the words say which.
            preset == null && template == PlanTemplates.DAYS -> guessArea(lower)
            preset == null && natural != null -> natural
            preset != null -> preset
            else -> guessArea(lower)
        }
        return PlanLine(
            raw = raw,
            title = title(template, rest, km, kg, money, currency),
            target = date?.first,
            amount = money?.first,
            currency = money?.second ?: currency.takeIf { money != null },
            km = km?.takeIf { template == PlanTemplates.RUN } ?: if (template == PlanTemplates.RUN) 5.0 else null,
            kg = kg?.takeIf { template == PlanTemplates.WEIGHT } ?: if (template == PlanTemplates.WEIGHT) 4.0 else null,
            count = count,
            days = days,
            subject = subject(template, rest, money),
            area = area,
            template = template,
        )
    }

    /**
     * Whether "Add anything" should offer to make a plan: a date ahead ("by March", "in 3 months")
     * or a goal word at the start ("save", "learn", "run a 5k"). Past tense stays a log.
     */
    fun looksLikePlan(raw: String, today: LocalDate): Boolean {
        val lower = raw.trim().lowercase()
        if (lower.isEmpty() || past.containsMatchIn(lower) || putAside(raw, "EUR") != null) return false
        if (findDate(lower, today) != null) return true
        if (Regex("""\b\d+\s?(?:min|mins|minutes|h|hrs|hours)\b""").containsMatchIn(lower)) return false
        return goalStart.containsMatchIn(lower.replace(lead, "")) || forDays.containsMatchIn(lower)
    }

    private val past = Regex("""\b(ran|jogged|walked|spent|bought|did|went|cooked|studied|finished|completed|swam|cycled|lifted|ate|had|was|were|this morning|yesterday|last night|today)\b""")
    private val goalStart = Regex(
        """^(save|saving up|learn|lose|pass|finish|build|write|become|visit|travel to|find a|land a|start a|launch|pay off|pay back|""" +
            """train for|prepare for|quit|stop|get promoted|get fit|get a job|get a new job|get my|run a|run the|run my first|""" +
            """read \d+ books|cook \d+|meditate|journal|sleep \d|go to|plan a)\b""",
    )

    /** "put aside 100 for Japan", "set aside €50", "saved 100 for the trip", "paid off 200". */
    fun putAside(raw: String, currency: String): PutAside? {
        val lower = raw.trim().lowercase()
        val m = Regex("""^(put aside|set aside|saved|paid off|paid back)\s+(.+)$""").find(lower) ?: return null
        val paidOff = m.groupValues[1].startsWith("paid")
        val rest = raw.trim().substring(m.groups[2]!!.range.first)
        val (amount, cur) = findMoney(rest, currency) ?: return null
        val subject = Regex("""\b(?:for|to|into|towards?|off)\s+(?:the\s+|my\s+|a\s+)?([\p{L}][\p{L}\s'-]{0,30})\s*$""", RegexOption.IGNORE_CASE)
            .find(rest)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() }
        return PutAside(amount, cur, subject, paidOff)
    }

    // ── Dates ────────────────────────────────────────────────────────────────

    /** The date a line names, and where it said it. Always ahead of [today]. */
    fun findDate(lower: String, today: LocalDate): Pair<LocalDate, IntRange>? {
        endOf.find(lower)?.let { m ->
            val w = m.groupValues[1]
            val d = when (w) {
                "year" -> LocalDate(today.year, 12, 31)
                "month" -> lastDay(today.year, today.month)
                else -> next(months.getValue(w), today, last = true)
            }
            return d to m.range
        }
        dayMonth.find(lower)?.let { m ->
            val day = m.groupValues[1].toInt()
            return date(months.getValue(m.groupValues[2]), day, m.groupValues[3].toIntOrNull(), today)?.let { it to m.range }
        }
        monthDay.find(lower)?.let { m ->
            val day = m.groupValues[2].toInt()
            return date(months.getValue(m.groupValues[1]), day, m.groupValues[3].toIntOrNull(), today)?.let { it to m.range }
        }
        monthOnly.find(lower)?.let { m ->
            // "for" only counts in front of a month ("for March"), never as "for a month".
            val y = m.groupValues[2].toIntOrNull()
            val month = months.getValue(m.groupValues[1])
            return (if (y != null) LocalDate(y, month, 1) else next(month, today, last = false)) to m.range
        }
        relative.find(lower)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: numberWords[m.groupValues[1].removePrefix("a ").trim()] ?: 1
            val d = when (m.groupValues[2]) {
                "day" -> today.plus(DatePeriod(days = n))
                "week" -> today.plus(DatePeriod(days = 7 * n))
                "month" -> today.plus(DatePeriod(months = n))
                else -> today.plus(DatePeriod(years = n))
            }
            return d to m.range
        }
        season.find(lower)?.let { m ->
            val (month, day) = when (m.groupValues[1]) {
                "spring" -> Month.MARCH to 1
                "summer" -> Month.JUNE to 1
                "autumn", "fall" -> Month.SEPTEMBER to 1
                "winter" -> Month.DECEMBER to 1
                "christmas" -> Month.DECEMBER to 25
                else -> Month.JANUARY to 1
            }
            return date(month, day, null, today)!! to m.range
        }
        thisNext.find(lower)?.let { m ->
            val next = m.groupValues[1] == "next"
            val d = when (m.groupValues[2]) {
                "year" -> LocalDate(today.year + if (next) 1 else 0, 12, 31)
                "month" -> today.plus(DatePeriod(months = if (next) 1 else 0)).let { lastDay(it.year, it.month) }
                else -> {
                    val sunday = today.plus(DatePeriod(days = DayOfWeek.SUNDAY.ordinal - today.dayOfWeek.ordinal))
                    if (next) sunday.plus(DatePeriod(days = 7)) else sunday
                }
            }
            return d.takeIf { it > today }?.let { it to m.range }
        }
        year.find(lower)?.let { m ->
            val y = m.groupValues[1].toInt()
            if (y > today.year) return LocalDate(y, 1, 1) to m.range
        }
        return null
    }

    private fun date(month: Month, day: Int, year: Int?, today: LocalDate): LocalDate? {
        if (day !in 1..31) return null
        fun make(y: Int) = runCatching { LocalDate(y, month, day) }.getOrNull() ?: lastDay(y, month)
        if (year != null) return make(year)
        val d = make(today.year)
        return if (d > today) d else make(today.year + 1)
    }

    /** The next time [month] comes: its first day, or its last with [last]. This month counts as next year's. */
    private fun next(month: Month, today: LocalDate, last: Boolean): LocalDate {
        val y = if (month.ordinal > today.month.ordinal) today.year else today.year + 1
        return if (last) lastDay(y, month) else LocalDate(y, month, 1)
    }

    private fun lastDay(y: Int, m: Month): LocalDate = LocalDate(y, m, 1).plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))

    // ── Money ────────────────────────────────────────────────────────────────

    private fun findMoney(text: String, currency: String): Pair<Double, String>? {
        fun amount(s: String, k: String) = s.replace(",", "").toDoubleOrNull()?.let { if (k.isNotEmpty()) it * 1000 else it }
        moneyBefore.find(text)?.let { m -> return amount(m.groupValues[2], m.groupValues[3])?.let { it to (symbols[m.groupValues[1]] ?: currency) } }
        moneyAfter.find(text)?.let { m ->
            val unit = m.groupValues[3].lowercase()
            return amount(m.groupValues[1], m.groupValues[2])?.let { it to (symbols[unit] ?: codes[unit] ?: currency) }
        }
        bareMoney.find(text)?.let { m -> return amount(m.groupValues[1], m.groupValues[2])?.takeIf { it > 0 }?.let { it to currency } }
        return null
    }

    // ── Templates, areas, titles ─────────────────────────────────────────────

    /** Templates whose area is never in doubt: a run is Fitness wherever it was typed. */
    private val STRONG = setOf(PlanTemplates.RUN, PlanTemplates.WEIGHT, PlanTemplates.SAVE, PlanTemplates.DEBT, PlanTemplates.TRIP, PlanTemplates.JOB)

    private fun templateFor(lower: String, km: Double?, kg: Double?, count: Int?, days: Int?): String? {
        fun has(re: String) = Regex(re).containsMatchIn(lower)
        return when {
            has("""\b(run|running|jog|jogging|couch to)\b""") && (km != null || has("""\bmarathon\b""")) || has("""\b(5|10)k\b|\bmarathon\b""") -> PlanTemplates.RUN
            has("""\b(lose|drop|shed)\b""") && (kg != null || has("""\bweight\b""")) -> PlanTemplates.WEIGHT
            count != null && has("""\b(push-?ups|pull-?ups|squats|sit-?ups|burpees)\b""") -> PlanTemplates.REPS
            has("""\b(pay off|pay back|clear|get out of)\b""") && has("""\b(debt|debts|loan|loans|card|overdraft|\d)""") -> PlanTemplates.DEBT
            has("""\b(save|saving|savings|put aside|set aside|emergency fund|nest egg)\b""") -> PlanTemplates.SAVE
            days != null -> PlanTemplates.DAYS
            has("""\bcook\b""") && (count != null || has("""\b(new|learn to cook)\b""")) -> PlanTemplates.COOK
            has("""\b(pass|exam|exams|ielts|toefl|gmat|gre|certificate|certification|driving test|license|licence)\b""") -> PlanTemplates.EXAM
            has("""\b(new job|find a job|get a job|land a job|job as|switch jobs|change jobs|change careers?|career change|get hired|new role)\b""") -> PlanTemplates.JOB
            has("""\b(promoted|promotion|a raise|pay rise)\b""") -> PlanTemplates.PROMOTION
            count != null && has("""\bread\b""") && has("""\bbooks?\b""") -> PlanTemplates.BOOKS
            has("""\b(learn|study|master|get better at|practise|practice|improve my)\b""") -> PlanTemplates.LEARN
            has("""\b(visit|trip|travel to|holiday|vacation|weekend in|week in|go to)\b""") -> PlanTemplates.TRIP
            has("""\bsleep\b""") -> PlanTemplates.SLEEP
            else -> null
        }
    }

    private fun areaOf(template: String): PlanArea = when (template) {
        PlanTemplates.RUN, PlanTemplates.WEIGHT, PlanTemplates.REPS -> PlanArea.FITNESS
        PlanTemplates.SAVE, PlanTemplates.DEBT -> PlanArea.MONEY
        PlanTemplates.LEARN, PlanTemplates.EXAM -> PlanArea.STUDY
        PlanTemplates.JOB, PlanTemplates.PROMOTION -> PlanArea.CAREER
        PlanTemplates.COOK -> PlanArea.MEALS
        PlanTemplates.TRIP -> PlanArea.TRAVEL
        PlanTemplates.SLEEP -> PlanArea.MIND
        else -> PlanArea.HABITS
    }

    private val areaWords = listOf(
        PlanArea.MIND to setOf("meditate", "meditation", "journal", "journaling", "sleep", "stress", "calm", "mindful", "anxiety", "therapy", "gratitude", "breathe"),
        PlanArea.MEALS to setOf("cook", "cooking", "eat", "eating", "meal", "meals", "diet", "vegan", "vegetarian", "vegetables", "recipe", "recipes", "sugar", "bake"),
        PlanArea.FITNESS to setOf("gym", "workout", "swim", "bike", "cycle", "yoga", "fit", "fitness", "hike", "climb", "stretch", "muscle", "strong", "abs", "steps", "walk"),
        PlanArea.MONEY to setOf("money", "budget", "invest", "investing", "earn", "income", "spend", "spending"),
        PlanArea.STUDY to setOf("course", "class", "language", "degree", "university", "school", "read", "reading"),
        PlanArea.CAREER to setOf("job", "career", "work", "cv", "resume", "interview", "business", "freelance", "portfolio", "client", "clients", "side"),
        PlanArea.TRAVEL to setOf("abroad", "travel", "flight", "country", "countries"),
    )

    private fun guessArea(lower: String): PlanArea {
        val words = lower.split(Regex("""[^\p{L}\p{N}]+""")).toSet()
        return areaWords.firstOrNull { (_, set) -> words.any { it in set } }?.first ?: PlanArea.HABITS
    }

    private fun title(template: String?, rest: String, km: Double?, kg: Double?, money: Pair<Double, String>?, currency: String): String = when (template) {
        PlanTemplates.RUN -> "Run a ${PlanTemplates.runName(km ?: 5.0)}"
        PlanTemplates.WEIGHT -> "Lose ${PlanTemplates.km(kg ?: 4.0)} kg"
        PlanTemplates.SAVE, PlanTemplates.DEBT -> money?.let { (amount, cur) ->
            val formatted = MoneyFormat.format(amount, cur)
            listOf(moneyBefore, moneyAfter, bareMoney).firstNotNullOfOrNull { re -> re.find(rest) }
                ?.let { m -> rest.replaceRange(m.range, formatted) } ?: "$rest $formatted"
        }?.let(::tidy) ?: rest
        else -> rest
    }.replaceFirstChar { it.uppercase() }

    private val trailing = Regex("""\s+(basics|the basics|properly|well|fluently|better|more|at last)$""", RegexOption.IGNORE_CASE)

    private fun subject(template: String?, rest: String, money: Pair<Double, String>?): String? {
        fun cap(s: String?) = s?.trim()?.trim(',', '.')?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() }
        return when (template) {
            // Kept as typed, article and all: "Put aside for a bike?", "Put aside for Japan?".
            PlanTemplates.SAVE -> Regex("""\bfor\s+((?:a\s+|an\s+|my\s+|the\s+|our\s+)?[\p{L}][\p{L}\s'-]{1,30})$""", RegexOption.IGNORE_CASE)
                .find(rest)?.groupValues?.get(1)?.let(::tidy)?.takeIf { it.isNotEmpty() }
            PlanTemplates.LEARN -> Regex("""\b(?:learn|study|master|get better at|practise|practice|improve my)\s+(?:to\s+|how\s+to\s+|some\s+|the\s+)?(.+)$""", RegexOption.IGNORE_CASE)
                .find(rest)?.groupValues?.get(1)?.replace(trailing, "")?.let(::cap)
            PlanTemplates.EXAM -> Regex("""\bpass\s+(?:my\s+|the\s+|an\s+|a\s+)?(.+)$""", RegexOption.IGNORE_CASE)
                .find(rest)?.groupValues?.get(1)?.let(::cap) ?: cap(rest)
            PlanTemplates.DAYS -> cap(rest.replace(forDays, " ").replace(Regex("""\bevery\s+day\b""", RegexOption.IGNORE_CASE), " ").let(::tidy))
            PlanTemplates.TRIP -> Regex("""\b(?:visit|travel to|go to|trip to|weekend in|week in|holiday in|holiday to|vacation in)\s+(.+)$""", RegexOption.IGNORE_CASE)
                .find(rest)?.groupValues?.get(1)?.let(::cap)
            PlanTemplates.REPS -> Regex("""\b(push-?ups|pull-?ups|squats|sit-?ups|burpees)\b""", RegexOption.IGNORE_CASE)
                .find(rest)?.groupValues?.get(1)?.lowercase()?.replace("pushups", "push-ups")?.replace("pullups", "pull-ups")?.replace("situps", "sit-ups")
            else -> null
        }
    }

    private fun tidy(s: String) = s.replace(Regex("""\s{2,}"""), " ").trim().trim(',', '.', '-', ' ')
}
