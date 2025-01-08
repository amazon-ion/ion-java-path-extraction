package com.amazon.ionpathextraction

import com.amazon.ion.IonReader
import com.amazon.ionpathextraction.exceptions.PathExtractionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class FsmPathExtractorTest : PathExtractorTest() {
    override fun <T> PathExtractorBuilder<T>.buildExtractor(): PathExtractor<T> = buildStrict()

    @ParameterizedTest
    @MethodSource("testCases")
    override fun testSearchPaths(testCase: Companion.TestCase) {
        if (testCase.legacyOnly) {
            assertThrows<UnsupportedPathExpression> {
                super.testSearchPaths(testCase)
            }
        } else {
            super.testSearchPaths(testCase)
        }
    }

    @ParameterizedTest
    @MethodSource("testCases")
    override fun testSearchPathsMatchCurrentValue(testCase: Companion.TestCase) {
        if (testCase.legacyOnly) {
            assertThrows<UnsupportedPathExpression> {
                super.testSearchPaths(testCase)
            }
        } else {
            super.testSearchPaths(testCase)
        }
    }

    data class TypingTestCase(val searchPath: String, val validity: List<Boolean>, val matchCount: Int)

    @Test
    fun testStrictTyping() {
        val inputs = listOf("17", "[31]", "(53)", "null", "{ foo: 67 }")
        val testCases = listOf(             //          17,  [31],  (53),  null, { foo: 67 }
            TypingTestCase("()",     listOf(true,  true,  true,  true, true), 5),
            TypingTestCase("A::()",  listOf(true,  true,  true,  true, true), 0),
            TypingTestCase("(*)",    listOf(false, true,  true,  true, true), 3),
            TypingTestCase("(A::*)", listOf(false, true,  true,  true, true), 0),
            TypingTestCase("(0)",    listOf(false, true,  true,  true, true), 3),
            TypingTestCase("(foo)",  listOf(false, false, false, true, true), 1))

        testCases.forEach { testCase ->
            var count = 0;
            val counter = { _: IonReader ->
                count += 1
                0
            }
            val extractor = PathExtractorBuilder.standard<Any>()
                .withSearchPath(testCase.searchPath, counter)
                .buildStrict(true)

            for (j in inputs.indices) {
                val ionReader = ION.newReader(inputs[j])
                if (testCase.validity[j]) {
                    extractor.match(ionReader)
                } else {
                    assertThrows<PathExtractionException> {
                        extractor.match(ionReader)
                    }
                }
            }
            assertEquals(testCase.matchCount, count)
        }
    }
}
