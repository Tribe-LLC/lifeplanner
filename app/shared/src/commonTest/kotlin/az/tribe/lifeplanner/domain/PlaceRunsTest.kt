package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.service.FitnessWeek
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.DayOfWeek.THURSDAY
import kotlinx.datetime.DayOfWeek.TUESDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceRunsTest {
    private val runs = setOf(MONDAY, WEDNESDAY, SATURDAY)

    @Test
    fun freeWeekKeepsTheDays() = assertEquals(runs, FitnessWeek.placeRuns(runs, emptySet()))

    @Test
    fun twoTakenDaysNeverShareOneFreeDay() {
        // Mon and Wed both used to land on Tue, and one run was lost.
        val placed = FitnessWeek.placeRuns(runs, setOf(MONDAY, WEDNESDAY))
        assertEquals(3, placed.size)
        assertEquals(setOf(TUESDAY, THURSDAY, SATURDAY), placed)
    }

    @Test
    fun fullWeekKeepsTheRunsOnTheirDays() =
        assertEquals(runs, FitnessWeek.placeRuns(runs, setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY)))

    @Test
    fun dayListReadsLikeASentence() {
        assertEquals("Mon, Wed and Sat", FitnessWeek.dayList(runs))
        assertEquals("Tue and Sat", FitnessWeek.dayList(setOf(SATURDAY, TUESDAY)))
        assertEquals("Sun", FitnessWeek.dayList(setOf(SUNDAY)))
    }
}
