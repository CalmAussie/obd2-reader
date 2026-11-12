package com.obdreader.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReadingGroupsTest {

    @Test fun everyReadingHasAGroup() {
        (Pids.all.map { it.hex } + Boost.KEY + "VOLT").forEach {
            assertNotEquals("$it has a group", ReadingGroups.Group.OTHER, ReadingGroups.of(it))
        }
    }

    @Test fun sortsByGroupThenByEachGroupsOrder() {
        val sorted = ReadingGroups.sort(listOf("VOLT", "07", "0C", "3C", "06", "0D", "ZZ"))
        assertEquals(listOf("Engine", "Fuel and mixture", "Exhaust", "Electrical", "Other"), sorted.map { it.first.label })
        assertEquals(listOf("0C", "0D"), sorted[0].second)
        assertEquals(listOf("06", "07"), sorted[1].second)
        assertEquals(listOf("ZZ"), sorted.last().second)
    }

    @Test fun emptyGroupsAreLeftOut() {
        assertEquals(listOf(ReadingGroups.Group.ELECTRICAL), ReadingGroups.sort(listOf("42")).map { it.first })
        assertEquals(emptyList<Any>(), ReadingGroups.sort(emptyList()))
    }

    @Test fun keysAreMatchedWhateverTheCase() {
        assertEquals(ReadingGroups.Group.EXHAUST, ReadingGroups.of("3c"))
    }
}
