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

package software.amazon.com.ionpathextraction;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;
import software.amazon.ion.IonReader;
import software.amazon.ion.system.IonReaderBuilder;

/**
 * Test the example code in README.md
 */
public class ExampleTest {

    @Test
    public void example() {
        final List<Integer> list = new ArrayList<>();
        final Function<IonReader, Integer> callback = (reader) -> {
            list.add(reader.intValue());

            return 0;
        };

        final PathExtractor pathExtractor = PathExtractorBuilder.standard()
            .withSearchPath("(foo)", callback)
            .withSearchPath("(bar)", callback)
            .withSearchPath("(baz 1)", callback)
            .build();

        final IonReader ionReader = IonReaderBuilder.standard().build("{foo: 1}"
                + "{bar: 2}"
                + "{baz: [10,20,30,40]}"
                + "{other: 99}"
        );

        pathExtractor.match(ionReader);

        assertEquals("[1, 2, 20]", list.toString());
    }
}
