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

import static software.amazon.ionpathextraction.internal.Preconditions.checkArgument;

import software.amazon.ion.IonReader;
import software.amazon.ionpathextraction.PathExtractorConfig;

/**
 * Text path component matches struct field names, example.
 * <pre>
 * data: {foo: [1,2,3], bar: { baz: [1] }}
 *
 * search path | callback invoked with reader at
 * ------------|--------------------
 *  (foo)      | [1, 2, 3]
 *  (bar baz)  | [1]
 * </pre>
 */
public final class Text implements PathComponent {

    private final String fieldName;

    /**
     * Constructor.
     *
     * @param fieldName component field name.
     */
    public Text(final String fieldName) {
        checkArgument(fieldName != null, "fieldName cannot be null");

        this.fieldName = fieldName;
    }

    @Override
    public boolean matches(final IonReader reader, final int currentPosition, final PathExtractorConfig config) {
        if (!reader.isInStruct()) {
            return false;
        }

        return config.isMatchCaseInsensitive()
            ? fieldName.equalsIgnoreCase(reader.getFieldName())
            : fieldName.equals(reader.getFieldName());
    }
}
