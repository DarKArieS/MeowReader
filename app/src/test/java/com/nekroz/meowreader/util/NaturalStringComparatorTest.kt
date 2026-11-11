package com.nekroz.meowreader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalStringComparatorTest {

    @Test
    fun `numbers are ordered by value instead of character by character`() {
        val names = listOf("第10章.txt", "第2章.txt", "第1章.txt", "第21章.txt", "第3章.txt")

        assertEquals(
            listOf("第1章.txt", "第2章.txt", "第3章.txt", "第10章.txt", "第21章.txt"),
            names.sortedWith(NaturalStringComparator),
        )
    }

    @Test
    fun `leading zeros do not change the numeric order`() {
        val names = listOf("vol010", "vol9", "vol08", "vol0")

        assertEquals(
            listOf("vol0", "vol08", "vol9", "vol010"),
            names.sortedWith(NaturalStringComparator),
        )
    }

    @Test
    fun `letter case is ignored`() {
        assertEquals(0, NaturalStringComparator.compare("Novel", "novel"))
        assertTrue(NaturalStringComparator.compare("apple", "Banana") < 0)
    }

    @Test
    fun `a prefix sorts before the longer name`() {
        assertTrue(NaturalStringComparator.compare("book", "book2") < 0)
        assertTrue(NaturalStringComparator.compare("book2", "book") > 0)
        assertEquals(0, NaturalStringComparator.compare("", ""))
    }

    @Test
    fun `digits of the same value with different text after them still compare the rest`() {
        assertTrue(NaturalStringComparator.compare("1a", "1b") < 0)
        assertTrue(NaturalStringComparator.compare("12-b", "12-a") > 0)
    }
}
