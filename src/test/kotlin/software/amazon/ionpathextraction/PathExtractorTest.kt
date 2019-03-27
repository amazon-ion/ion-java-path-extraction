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

package software.amazon.ionpathextraction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import software.amazon.ionpathextraction.exceptions.PathExtractionException
import software.amazon.ionpathextraction.pathcomponents.PathComponent
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
                            val searchPaths = if (struct.containsKey("searchPath")) {
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

    private fun collectToIonList(stepOutN: Int): (IonReader, IonList) -> Int = { reader, out ->
        ION.newWriter(out).use { it.writeValue(reader) }
        stepOutN
    }

    @ParameterizedTest
    @MethodSource("testCases")
    fun testSearchPaths(testCase: TestCase) {

        val builder = PathExtractorBuilder.standard<IonList>()

        testCase.searchPaths.forEach { builder.withSearchPath(it, collectToIonList(testCase.stepOutNumber)) }
        val extractor = builder.build()

        val out = ION.newEmptyList()
        extractor.match(ION.newReader(testCase.data), out)

        assertEquals(testCase.expected, out)
    }

    @Test
    fun testCorrectCallbackCalled() {
        var timesCallback1Called = 0
        var timesCallback2Called = 0

        val extractor: PathExtractor<Any> = PathExtractorBuilder.standard<Any>()
                .withSearchPath("(foo)") { _ ->
                    timesCallback1Called++
                    0
                }
                .withSearchPath("(bar)") { _ ->
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
        val extractor = PathExtractorBuilder.standard<Any>()
                .withSearchPath("(foo)") { _ -> 0 }
                .build()

        val reader = ION.newReader("[{foo: 1}]")
        assertTrue(reader.next() != null)
        reader.stepIn()

        val exception = assertThrows<PathExtractionException> { extractor.match(reader) }
        assertEquals("reader must be at depth zero, it was at:1", exception.message)
    }

    @Test
    fun matchRelative() {
        val extractor = PathExtractorBuilder.standard<IonList>()
                .withMatchRelativePaths(true)
                .withSearchPath("(foo)", collectToIonList(0))
                .build()

        val reader = ION.newReader("[{foo: 1}]")
        assertTrue(reader.next() != null)
        reader.stepIn()

        val out = ION.newEmptyList()
        extractor.match(reader, out)

        assertEquals(ION.singleValue("[1]"), out)
    }

    @Test
    fun caseInsensitive() {
        val extractor = PathExtractorBuilder.standard<IonList>()
                .withMatchCaseInsensitive(true)
                .withSearchPath("(foo)", collectToIonList(0))
                .build()

        val out = ION.newEmptyList()
        extractor.match(ION.newReader("{FOO: 1}{foo: 2}{fOo: 3}{bar: 4}"), out)

        assertEquals(ION.singleValue("[1,2,3]"), out)
    }

    @Test
    fun stepOutMoreThanPermitted() {
        val extractor = PathExtractorBuilder.standard<Any>()
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
        val extractor = PathExtractorBuilder.standard<Any>()
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

    @Test
    fun nestedSearchPaths() {
        // Test only that the correct callbacks were called as reading the value for (foo)
        // will advance the reader making (foo bar) not match

        val counter = mutableMapOf(
                "()" to 0,
                "(foo)" to 0,
                "(foo bar)" to 0
        )

        val extractor = PathExtractorBuilder.standard<Any>().apply {
            counter.forEach { sp, _ ->
                withSearchPath(sp) { _ ->
                    counter[sp] = counter[sp]!! + 1
                    0
                }
            }
        }.build()

        extractor.match(ION.newReader("{foo: {bar: 1}}"))

        assertEquals(3, counter.size)
        assertEquals(1, counter["()"])
        assertEquals(1, counter["(foo)"])
        assertEquals(1, counter["(foo bar)"])
    }

    // Invalid configuration -----------------------------------------------------------------------------

    @Test
    fun nullStringPath() {
        val exception = assertThrows<PathExtractionException> {
            PathExtractorBuilder.standard<Any>().withSearchPath(null as String?, emptyCallback)
        }

        assertEquals("searchPathAsIon cannot be null", exception.message)
    }

    @Test
    fun nullListPath() {
        val exception = assertThrows<PathExtractionException> {
            PathExtractorBuilder.standard<Any>().withSearchPath(null as List<PathComponent>?, emptyCallback)
        }

        assertEquals("pathComponents cannot be null", exception.message)
    }

    @Test
    fun nullCallback() {
        val exception = assertThrows<PathExtractionException> {
            val callback: java.util.function.Function<IonReader, Int>? = null

            PathExtractorBuilder.standard<Any>().withSearchPath("(foo)", callback)
        }

        assertEquals("callback cannot be null", exception.message)
    }

    @Test
    fun emptySearchPath() {
        val exception = assertThrows<PathExtractionException> {
            PathExtractorBuilder.standard<Any>().withSearchPath("", emptyCallback)
        }

        assertEquals("ionPathExpression cannot be empty", exception.message)
    }

    @Test
    fun searchPathNotSequence() {
        val exception = assertThrows<PathExtractionException> {
            PathExtractorBuilder.standard<Any>().withSearchPath("1", emptyCallback)
        }

        assertEquals("ionPathExpression must be a s-expression or list", exception.message)
    }
}