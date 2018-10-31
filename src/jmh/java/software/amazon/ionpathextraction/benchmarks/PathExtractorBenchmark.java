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

package software.amazon.ionpathextraction.benchmarks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.function.Function;
import java.util.stream.Stream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import software.amazon.ion.IonReader;
import software.amazon.ion.IonSystem;
import software.amazon.ion.IonWriter;
import software.amazon.ion.system.IonBinaryWriterBuilder;
import software.amazon.ion.system.IonReaderBuilder;
import software.amazon.ion.system.IonSystemBuilder;
import software.amazon.ion.system.IonTextWriterBuilder;
import software.amazon.ionpathextraction.PathExtractor;
import software.amazon.ionpathextraction.PathExtractorBuilder;

/**
 * Benchmarks comparing the PathExtractor with fully materializing the DOM.
 */
public class PathExtractorBenchmark {

    private static final IonSystem DOM_FACTORY = IonSystemBuilder.standard().build();

    private static IonReader newReader(final InputStream inputStream) {
        return IonReaderBuilder.standard().build(inputStream);
    }

    private static IonWriter newBinaryWriter(final OutputStream outputStream) {
        return IonBinaryWriterBuilder.standard().build(outputStream);
    }

    private static IonWriter newTextWriter(final OutputStream outputStream) {
        return IonTextWriterBuilder.standard().build(outputStream);
    }

    private static final String DATA_URL = "https://data.nasa.gov/data.json";
    private static byte[] bytesBinary;
    private static byte[] bytesText;

    private static void setupTestData() throws IOException {
        final URL url = new URL(DATA_URL);

        final ByteArrayOutputStream binaryOut = new ByteArrayOutputStream();
        try (
            final InputStream inputStream = url.openStream();
            final IonReader reader = newReader(inputStream);
            final IonWriter writer = newBinaryWriter(binaryOut)
        ) {
            // all data is in the `dataset` key as a list, only write out that field to keep the extractor smaller
            reader.next();
            reader.stepIn();
            while (reader.next() != null) {
                if (reader.getFieldName().equals("dataset")) {
                    reader.stepIn();
                    writer.writeValues(reader);
                    reader.stepOut();
                }
            }
            reader.stepOut();
        }

        bytesBinary = binaryOut.toByteArray();

        final ByteArrayOutputStream textOut = new ByteArrayOutputStream();
        // text version
        try (
            final InputStream inputStream = new ByteArrayInputStream(bytesBinary);
            final IonReader reader = newReader(inputStream);
            final IonWriter writer = newTextWriter(textOut)
        ) {
            writer.writeValues(reader);
        }

        bytesText = textOut.toByteArray();
    }

    /**
     * sets up shared test data once.
     */
    static {
        try {
            setupTestData();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Each thread gets a single instance.
     */
    @State(Scope.Thread)
    public static class ThreadState {

        PathExtractor pathExtractor;
        PathExtractor pathExtractorPartial;
        PathExtractor pathExtractorPartialNoDom;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            pathExtractor = makePathExtractor(reader -> {
                    // reads matches as DOM doing similar work as the DOM loader
                    DOM_FACTORY.newValue(reader);
                    return 0;
                },
                "(@type)",
                "(accessLevel)",
                "(accrualPeriodicity)",
                "(bureauCode)",
                "(contactPoint)",
                "(description)",
                "(distribution)",
                "(identifier)",
                "(issued)",
                "(keyword)",
                "(landingPage)",
                "(modified)",
                "(programCode)",
                "(publisher)",
                "(title)",
                "(license)"
            );

            pathExtractorPartial = makePathExtractor(reader -> {
                    // reads matches as DOM doing similar work as the DOM loader but only for matched values
                    DOM_FACTORY.newValue(reader);
                    return 0;
                },
                "(accessLevel)"
            );

            pathExtractorPartialNoDom = makePathExtractor(reader -> {
                    // reads the value without materializing a DOM object
                    reader.stringValue(); // accessLevel is a string
                    return 0;
                },
                "(accessLevel)"
            );
        }

        private PathExtractor makePathExtractor(final Function<IonReader, Integer> callback,
                                                final String... searchPaths) {
            final PathExtractorBuilder builder = PathExtractorBuilder.standard();
            Stream.of(searchPaths).forEach(sp -> builder.withSearchPath(sp, callback));
            return builder.build();
        }
    }

    /**
     * Fully materializes all struct fields as IonValues using a path extractor.
     */
    @Benchmark
    public Object fullBinary(final ThreadState threadState) {
        // instantiate reader inside benchmark to be more comparable to dom loading
        IonReader reader = newReader(new ByteArrayInputStream(bytesBinary));
        threadState.pathExtractor.match(reader);

        return reader;
    }

    /**
     * Text version of {@link #fullBinary(ThreadState)}.
     */
    @Benchmark
    public Object fullText(final ThreadState threadState) {
        // instantiate reader inside benchmark to be more comparable to dom loading
        IonReader reader = newReader(new ByteArrayInputStream(bytesText));
        threadState.pathExtractor.match(reader);

        return reader;
    }

    /**
     * Materializes a single struct fields as IonValue using a path extractor.
     */
    @Benchmark
    public Object partialBinary(final ThreadState threadState) {
        // instantiate reader inside benchmark to be more comparable to dom loading
        IonReader reader = newReader(new ByteArrayInputStream(bytesBinary));
        threadState.pathExtractorPartial.match(reader);

        return reader;
    }

    /**
     * Text version of {@link #partialBinary(ThreadState)}.
     */
    @Benchmark
    public Object partialText(final ThreadState threadState) {
        // instantiate reader inside benchmark to be more comparable to dom loading
        IonReader reader = newReader(new ByteArrayInputStream(bytesText));
        threadState.pathExtractorPartial.match(reader);

        return reader;
    }

    /**
     * Access the java representation directly of a single struct field without materializing an `IonValue`.
     */
    @Benchmark
    public Object partialBinaryNoDom(final ThreadState threadState) {
        // instantiate reader inside benchmark to be more comparable to dom loading
        IonReader reader = newReader(new ByteArrayInputStream(bytesBinary));
        threadState.pathExtractorPartialNoDom.match(reader);

        return reader;
    }

    /**
     * Text version of {@link #partialBinaryNoDom(ThreadState)}.
     */
    @Benchmark
    public Object partialTextNoDom(final ThreadState threadState) {
        // instantiate reader inside benchmark to be more comparable to dom loading
        IonReader reader = newReader(new ByteArrayInputStream(bytesText));
        threadState.pathExtractorPartialNoDom.match(reader);

        return reader;
    }

    /**
     * Fully materializes a DOM for the file using an IonLoader.
     */
    @Benchmark
    public Object domBinary() {
        return DOM_FACTORY.getLoader().load(bytesBinary);
    }

    /**
     * Text version of {@link #domBinary()}.
     */
    @Benchmark
    public Object domText() {
        return DOM_FACTORY.getLoader().load(bytesText);
    }
}
