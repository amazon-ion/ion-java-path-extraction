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

package com.amazon.ionpathextraction;

import static org.junit.Assert.assertEquals;

import com.amazon.ion.IonReader;
import com.amazon.ion.system.IonReaderBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.Test;

/**
 * Test the example code in README.md
 */
public class ExampleTest {

    @Test
    public void example() {
        final AtomicLong counter = new AtomicLong(0);

        final Function<IonReader, Integer> callback = (reader) -> {
            counter.addAndGet(reader.intValue());

            return 0;
        };

        final PathExtractor<?> pathExtractor = PathExtractorBuilder.standard()
            .withSearchPath("(foo)", callback)
            .withSearchPath("(bar)", callback)
            .withSearchPath("(A::baz 1)", callback)
            .build();

        final IonReader ionReader = IonReaderBuilder.standard().build("{foo: 1}"
            + "{bar: 2}"
            + "{baz: A::[10,20,30,40]}"
            + "{baz: [100,200,300,400]}"
            + "{other: 99}"
        );

        pathExtractor.match(ionReader);

        assertEquals(23, counter.get());
    }

    @Test
    public void topLevelExample() {
        final AtomicLong counterA = new AtomicLong(0);
        final AtomicLong counterB = new AtomicLong(0);

        final PathExtractor<?> pathExtractor = PathExtractorBuilder.standard()
            .withSearchPath("()", (reader) -> {
                counterA.addAndGet(reader.intValue());

                return 0;
            })
            .withSearchPath("A::()", (reader) -> {
                counterB.addAndGet(reader.intValue());

                return 0;
            })
            .build();

        final IonReader ionReader = IonReaderBuilder.standard().build("1 1 1 A::10 1");

        pathExtractor.match(ionReader);

        assertEquals(14, counterA.get());
        assertEquals(10, counterB.get());
    }

    @Test
    public void exampleWithContext() {

        final BiFunction<IonReader, List<Integer>, Integer> callback = (reader, list) -> {
            list.add(reader.intValue());

            return 0;
        };

        final PathExtractor<List<Integer>> pathExtractor = PathExtractorBuilder.<List<Integer>>standard()
            .withSearchPath("(foo)", callback)
            .withSearchPath("(bar)", callback)
            .withSearchPath("(A::baz 1)", callback)
            .build();

        final IonReader ionReader = IonReaderBuilder.standard().build("{foo: 1}"
            + "{bar: 2}"
            + "{baz: A::[10,20,30,40]}"
            + "{baz: [100,200,300,400]}"
            + "{other: 99}"
        );

        final List<Integer> list = new ArrayList<>();
        pathExtractor.match(ionReader, list);

        assertEquals("[1, 2, 20]", list.toString());
    }
}
