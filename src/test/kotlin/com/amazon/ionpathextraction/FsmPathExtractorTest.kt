package com.amazon.ionpathextraction

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
}