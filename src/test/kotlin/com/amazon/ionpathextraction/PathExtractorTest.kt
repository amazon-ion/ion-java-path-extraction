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

package com.amazon.ionpathextraction

import com.amazon.ion.*
import com.amazon.ion.system.*
import com.amazon.ionpathextraction.exceptions.PathExtractionException
import com.amazon.ionpathextraction.pathcomponents.PathComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.stream.Stream
import kotlin.test.assertTrue

abstract class PathExtractorTest {
    companion object {
        val ION: IonSystem = IonSystemBuilder.standard().build()

        data class TestCase(val searchPaths: List<String>,
                            val data: String,
                            val expected: IonList,
                            val stepOutNumber: Int,
                            val hasMultipleTopLevelValues: Boolean,
                            val legacyOnly: Boolean = false,
                            val caseInsensitive: String = "None") {
            override fun toString(): String = "SearchPaths=$searchPaths, " +
                    "Data=$data, " +
                    "Expected=$expected, " +
                    "StepOutN=$stepOutNumber" +
                    "Legacy=$legacyOnly" +
                    "CaseInsensitive=$caseInsensitive"
        }

        private fun IonValue.toText(): String {
            val out = StringBuilder()

            ION.newTextWriter(out).use { writer ->
                if (hasTypeAnnotation("${'$'}datagram") && this is IonContainer) {
                    forEach { it -> it.writeTo(writer) }
                } else {
                    this.writeTo(writer)
                }
            }

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
                                    struct["stepOutN"]?.let { (it as IonInt).intValue() } ?: 0,
                                    struct["data"].hasTypeAnnotation("${'$'}datagram"),
                                    struct.hasTypeAnnotation("legacy"),
                                    struct.get("caseInsensitive")?.toText() ?: "None"
                            )
                        }.stream()

        enum class API {
            MATCH {
                override fun <T> match(extractor: PathExtractor<T>, reader: IonReader, context: T?) {
                    extractor.match(reader, context)
                }
            },
            MATCH_CURRENT_VALUE {
                override fun <T> match(extractor: PathExtractor<T>, reader: IonReader, context: T?) {
                    reader.next()
                    extractor.matchCurrentValue(reader, context)
                }
            };

            abstract fun <T> match(extractor: PathExtractor<T>, reader: IonReader, context: T? = null)
        }
    }

    abstract fun <T> PathExtractorBuilder<T>.buildExtractor(): PathExtractor<T>

    private val emptyCallback: (IonReader) -> Int = { 0 }

    private fun collectToIonList(stepOutN: Int): (IonReader, IonList) -> Int = { reader, out ->
        ION.newWriter(out).use { it.writeValue(reader) }
        stepOutN
    }

    @ParameterizedTest
    @MethodSource("testCases")
    open fun testSearchPaths(testCase: TestCase) {

        val builder = PathExtractorBuilder.standard<IonList>()

        testCase.searchPaths.forEach { builder.withSearchPath(it, collectToIonList(testCase.stepOutNumber)) }
        when (testCase.caseInsensitive) {
            "Both" -> builder.withMatchCaseInsensitive(true)
            "Fields" -> builder.withMatchFieldNamesCaseInsensitive(true)
            "None" -> Unit
            else -> throw IllegalArgumentException("Unexpected value for caseInsensitive: ${testCase.caseInsensitive}")
        }
        val extractor = builder.buildExtractor()

        val out = ION.newEmptyList()
        extractor.match(ION.newReader(testCase.data), out)

        assertEquals(testCase.expected, out, testCase.toString())
    }

    @ParameterizedTest
    @MethodSource("testCases")
    open fun testSearchPathsMatchCurrentValue(testCase: TestCase) {
        if (testCase.hasMultipleTopLevelValues) {
            // For simplicity, skip tests with multiple top-level values. This will be tested via other test methods.
            return
        }
        val builder = PathExtractorBuilder.standard<IonList>()

        testCase.searchPaths.forEach { builder.withSearchPath(it, collectToIonList(testCase.stepOutNumber)) }
        val extractor = builder.buildExtractor()

        val out = ION.newEmptyList()
        val reader = ION.newReader(testCase.data)
        reader.next()
        val depth = reader.depth
        extractor.matchCurrentValue(reader, out)

        assertEquals(depth, reader.depth)
        assertEquals(testCase.expected, out, testCase.toString())
    }

    @ParameterizedTest
    @EnumSource(API::class)
    fun testCorrectCallbackCalled(api: API) {
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
                .buildExtractor()

        api.match(extractor, ION.newReader("{ bar: 1, bar: 2, foo: 3 }"))

        assertAll(
                { assertEquals(1, timesCallback1Called) },
                { assertEquals(2, timesCallback2Called) }
        )
    }

    @Test
    fun matchCurrentValueOnlyMatchesCurrentValue() {
        val extractor1 = PathExtractorBuilder.standard<IonList>()
                .withSearchPath("(foo)", collectToIonList(0))
                .buildExtractor()
        val extractor2 = PathExtractorBuilder.standard<IonList>()
                .withSearchPath("(*)", collectToIonList(1))
                .withMatchRelativePaths(true)
                .buildExtractor()

        val reader = ION.newReader("{foo: 123, foo: [456]} {bar: [42, 43, 44]} end")
        val out = ION.newEmptyList()
        assertEquals(IonType.STRUCT, reader.next())
        extractor1.matchCurrentValue(reader, out)
        assertEquals(ION.singleValue("[123, [456]]"), out)
        assertEquals(IonType.STRUCT, reader.next())
        reader.stepIn()
        assertEquals(IonType.LIST, reader.next())
        assertEquals("bar", reader.fieldName)
        extractor2.matchCurrentValue(reader, out)
        assertEquals(ION.singleValue("[123, [456], 42]"), out)
        assertEquals(1, reader.depth)
        reader.stepOut()
        assertEquals(IonType.SYMBOL, reader.next())
        assertEquals("end", reader.stringValue())
        assertNull(reader.next())
    }

    @Test
    fun matchCurrentValueWhenNotPositionedOnValueFails() {
        val extractor = PathExtractorBuilder.standard<Any>()
                .withSearchPath("(foo)") { _ -> 0 }
                .buildExtractor()

        val reader = ION.newReader("[{foo: 1}]")
        val exception = assertThrows<PathExtractionException> { extractor.matchCurrentValue(reader) }
        assertEquals("reader must be positioned at a value; call IonReader.next() first.", exception.message)
    }

    @ParameterizedTest
    @EnumSource(API::class)
    fun readerAtInvalidDepth(api: API) {
        val extractor = PathExtractorBuilder.standard<Any>()
                .withSearchPath("(foo)") { _ -> 0 }
                .buildExtractor()

        val reader = ION.newReader("[{foo: 1}]")
        assertTrue(reader.next() != null)
        reader.stepIn()

        val exception = assertThrows<PathExtractionException> { api.match(extractor, reader) }
        assertEquals("reader must be at depth zero, it was at: 1", exception.message)
    }

    @ParameterizedTest
    @EnumSource(API::class)
    fun matchRelative(api: API) {
        val extractor = PathExtractorBuilder.standard<IonList>()
                .withMatchRelativePaths(true)
                .withSearchPath("(foo)", collectToIonList(0))
                .buildExtractor()

        val reader = ION.newReader("[{foo: 1}]")
        assertTrue(reader.next() != null)
        reader.stepIn()

        val out = ION.newEmptyList()
        api.match(extractor, reader, out)

        assertEquals(ION.singleValue("[1]"), out)
    }

    @ParameterizedTest
    @EnumSource(API::class)
    fun stepOutMoreThanPermitted(api: API) {
        val extractor = PathExtractorBuilder.standard<Any>()
                .withSearchPath("(foo)") { _ -> 200 }
                .buildExtractor()

        val exception = assertThrows<PathExtractionException> {
            api.match(extractor, ION.newReader("{foo: 1}"))
        }

        assertEquals("Callback return cannot be greater than the reader current relative depth. " +
                "return: 200, relative reader depth: 1", exception.message)
    }

    @ParameterizedTest
    @EnumSource(API::class)
    fun stepOutMoreThanPermittedWithRelative(api: API) {
        val extractor = PathExtractorBuilder.standard<Any>()
                .withMatchRelativePaths(true)
                // even though you could step out twice in reader you can't given the initial reader depth
                .withSearchPath("(bar)") { _ -> 2 }
                .buildExtractor()

        val newReader = ION.newReader("{foo: {bar: 1}}")
        newReader.next()
        newReader.stepIn() // positioned at the beginning of {bar: 1}

        val exception = assertThrows<PathExtractionException> {
            api.match(extractor, newReader)
        }

        assertEquals("Callback return cannot be greater than the reader current relative depth. return: 2, " +
                "relative reader depth: 1", exception.message)
    }

    @ParameterizedTest
    @EnumSource(API::class)
    fun nestedSearchPaths(api: API) {
        // Test only that the correct callbacks were called as reading the value for (foo)
        // will advance the reader making (foo bar) not match

        val counter = mutableMapOf(
                "()" to 0,
                "(foo)" to 0,
                "(foo bar)" to 0
        )

        val extractor = PathExtractorBuilder.standard<Any>().apply {
            counter.forEach { (sp, _) ->
                withSearchPath(sp) { _ ->
                    counter[sp] = counter[sp]!! + 1
                    0
                }
            }
        }.buildExtractor()

        api.match(extractor, ION.newReader("{foo: {bar: 1}}"))

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
            PathExtractorBuilder.standard<Any>().withSearchPath(null as List<PathComponent>?, emptyCallback, emptyArray())
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

    private fun newReader(value: IonValue, isBinary: Boolean): IonReader {
        val baos = ByteArrayOutputStream()
        val ionWriter = if (isBinary) IonBinaryWriterBuilder.standard().build(baos) else IonTextWriterBuilder.standard().build(baos)
        value.writeTo(ionWriter)
        ionWriter.close()
        return IonReaderBuilder.standard().build(baos.toByteArray())
    }

    @ParameterizedTest
    @ValueSource(strings = ["binary", "text"])
    fun evaluateSecondPathOnTheSameValueAfterTheFirstPathMatches(encoding: String) {
        val value = ION.singleValue("{col1:\"foo\", col2:[{col21:\"bar\",col22:12}]}") as IonStruct
        val ionReader = newReader(value, encoding == "binary")
        val extractor = PathExtractorBuilder.standard<Any>().withSearchPath("(col2)") { ionReader1: IonReader? ->
            val actualData = ION.newValue(ionReader1)
            assertEquals(value["col2"], actualData)
            0
        }.withSearchPath("(col1)") { _ -> 0 }.buildExtractor()
        extractor.match(ionReader)
    }
}