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

package software.amazon.ionpathextraction.pathcomponents;

import software.amazon.ion.IonReader;
import software.amazon.ionpathextraction.PathExtractorConfig;

/**
 * Index path component matches collection by position, example.
 * <pre>
 * data: {foo: [1,2,3], bar: { baz: [1] }}
 *
 * search path | callback invoked with reader at
 * ------------|--------------------
 *  (0)        | [1, 2, 3]
 *  (0 2)      | 3
 * </pre>
 */
public class Index implements PathComponent {

    private final int ordinal;

    /**
     * Constructor.
     *
     * @param ordinal component ordinal.
     */
    public Index(final int ordinal) {
        this.ordinal = ordinal;
    }

    @Override
    public boolean matches(final IonReader reader, final int currentPosition, final PathExtractorConfig config) {
        return ordinal == currentPosition;
    }
}
