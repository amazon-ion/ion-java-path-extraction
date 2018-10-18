/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package software.amazon.com.ionpathextraction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import software.amazon.com.ionpathextraction.PathExtractorTest.Companion.toText
import software.amazon.com.ionpathextraction.exceptions.PathExtractionException
import software.amazon.com.ionpathextraction.pathcomponents.PathComponent
import software.amazon.ion.*
import software.amazon.ion.system.IonSystemBuilder
import java.io.File
import java.util.stream.Stream
import kotlin.test.assertTrue

class PathExtractorTest {
    companion object {
        private val ION = IonSystemBuilder.standard().build()

        data class TestCase(val searchPaths: List<String>,
                            val data: String,
                            val expected: IonList,
                            val stepOutNumber: Int) {
            override fun toString(): String = "SearchPaths=$searchPaths, " +
                    "Data=$data, " +
                    "Expected=$expected, " +
                    "StepOutN=$stepOutNumber"
        }

        private fun IonValue.toText(): String {
            val out = StringBuilder()
            ION.newTextWriter(out).use { this.writeTo(it) }
            return out.toString()
        }

        @JvmStatic
        fun testCases(): Stream<TestCase> =
                ION.loader.load(File("src/test/resources/test-cases.ion"))
                        .map { it as IonStruct }
                        .map { struct ->

                            // single
                            val searchPaths = if(struct.containsKey("searchPath")) {
                                listOf(struct["searchPath"].toText())
                            }
                            // multiple
                            else {
                                (struct["searchPaths"] as IonSequence).map { it.toText() }
                            }

                            TestCase(
                                    searchPaths,
                                    struct["data"].toText(),
                                    struct["expected"] as IonList,
                                    struct["stepOutN"]?.let { (it as IonInt).intValue() } ?: 0
                            )
                        }.stream()
    }

    private val emptyCallback: (IonReader) -> Int = { 0 }

    private fun collectToIonList(out: IonList, stepOutN: Int): (IonReader) -> Int = { reader ->
        ION.newWriter(out).use { it.writeValue(reader) }
        stepOutN
    }

    @ParameterizedTest
    @MethodSource("testCases")
    fun testSearchPaths(testCase: TestCase) {
        val out = ION.newEmptyList()

        val builder = PathExtractorBuilder.standard()
        testCase.searchPaths.forEach { builder.withSearchPath(it, collectToIonList(out, testCase.stepOutNumber)) }
        val extractor = builder.build()

        extractor.match(ION.newReader(testCase.data))

        assertEquals(testCase.expected, out)
    }

    @Test
    fun testCorrectCallbackCalled() {
        var timesCallback1Called = 0
        var timesCallback2Called = 0

        val extractor = PathExtractorBuilder.standard()
                .withSearchPath("(foo)") {
                    timesCallback1Called++
                    0
                }
                .withSearchPath("(bar)") {
                    timesCallback2Called++
                    0
                }
                .build()

        extractor.match(ION.newReader("{ bar: 1, bar: 2, foo: 3 }"))

        assertAll(
                { assertEquals(1, timesCallback1Called) },
                { assertEquals(2, timesCallback2Called) }
        )
    }

    @Test
    fun readerAtInvalidDepth() {
        val extractor = PathExtractorBuilder.standard()
                .withSearchPath("(foo)") { 0 }
                .build()

        val reader = ION.newReader("[{foo: 1}]")
        assertTrue(reader.next() != null)
        reader.stepIn()

        val exception = assertThrows<PathExtractionException> { extractor.match(reader) }
        assertEquals("reader must be at depth zero, it was at:1", exception.message)
    }

    @Test
    fun matchRelative() {
        val out = ION.newEmptyList()
        val extractor = PathExtractorBuilder.standard()
                .withMatchRelativePaths(true)
                .withSearchPath("(foo)", collectToIonList(out, 0))
                .build()

        val reader = ION.newReader("[{foo: 1}]")
        assertTrue(reader.next() != null)
        reader.stepIn()

        extractor.match(reader)

        assertEquals(ION.singleValue("[1]"), out)
    }

    @Test
    fun caseInsensitive() {
        val out = ION.newEmptyList()
        val extractor = PathExtractorBuilder.standard()
                .withMatchCaseInsensitive(true)
                .withSearchPath("(foo)", collectToIonList(out, 0))
                .build()

        extractor.match(ION.newReader("{FOO: 1}{foo: 2}{fOo: 3}{bar: 4}"))

        assertEquals(ION.singleValue("[1,2,3]"), out)
    }

    @Test
    fun stepOutMoreThanPermitted() {
        val extractor = PathExtractorBuilder.standard()
                .withSearchPath("(foo)") { _ -> 200 }
                .build()

        val exception = assertThrows<PathExtractionException> {
            extractor.match(ION.newReader("{foo: 1}"))
        }

        assertEquals("Callback return cannot be greater than the reader current relative depth. " +
                "return: 200, relative reader depth: 1", exception.message)
    }

    @Test
    fun stepOutMoreThanPermittedWithRelative() {
        val extractor = PathExtractorBuilder.standard()
                .withMatchRelativePaths(true)
                // even though you could step out twice in reader you can't given the initial reader depth
                .withSearchPath("(bar)") { _ -> 2 }
                .build()

        val newReader = ION.newReader("{foo: {bar: 1}}")
        newReader.next()
        newReader.stepIn() // positioned at the beginning of {bar: 1}

        val exception = assertThrows<PathExtractionException> {
            extractor.match(newReader)
        }

        assertEquals("Callback return cannot be greater than the reader current relative depth. return: 2, " +
                "relative reader depth: 1", exception.message)
    }

    // Invalid configuration -----------------------------------------------------------------------------

    @Test
    fun nullStringPath() {
        val exception = assertThrows<PathExtractionException> {
            PathExtractorBuilder.standard().withSearchPath(null as String?, emptyCallback)
        }

        assertEquals("searchPathAsIon cannot be null", exception.message)
    }

    @Test
    fun nullListPath() {
        val exception = assertThrows<PathExtractionException> {
            PathExtractorBuilder.standard().withSearchPath(null as List<PathComponent>?, emptyCallback)
        }

        assertEquals("pathComponents cannot be null", exception.message)
    }

    @Test
    fun nullCallback() {
        val exception = assertThrows<PathExtractionException> {
            PathExtractorBuilder.standard().withSearchPath("(foo)", null)
        }

        assertEquals("callback cannot be null", exception.message)
    }

    @Test
    fun emptySearchPath() {
        val exception = assertThrows<PathExtractionException> {
            PathExtractorBuilder.standard().withSearchPath("", emptyCallback)
        }

        assertEquals("ionPathExpression cannot be empty", exception.message)
    }

    @Test
    fun searchPathNotSequence() {
        val exception = assertThrows<PathExtractionException> {
            PathExtractorBuilder.standard().withSearchPath("1", emptyCallback)
        }

        assertEquals("ionPathExpression must be a s-expression or list", exception.message)
    }
}