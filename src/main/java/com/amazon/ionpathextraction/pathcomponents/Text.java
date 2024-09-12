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

package com.amazon.ionpathextraction.pathcomponents;

import static com.amazon.ionpathextraction.internal.Preconditions.checkArgument;

import com.amazon.ion.IonReader;
import com.amazon.ionpathextraction.internal.Annotations;
import com.amazon.ionpathextraction.internal.MatchContext;

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
public final class Text extends PathComponent {

    private final String fieldName;

    /**
     * Constructor.
     *
     * @param fieldName component field name.
     */
    public Text(final String fieldName, final String[] annotations) {
        super(new Annotations(annotations));
        checkArgument(fieldName != null, "fieldName cannot be null");

        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    @Override
    public boolean innerMatches(final MatchContext context) {
        final IonReader reader = context.getReader();
        if (!reader.isInStruct()) {
            return false;
        }

        return context.getConfig().isMatchFieldsCaseInsensitive()
            ? fieldName.equalsIgnoreCase(reader.getFieldName())
            : fieldName.equals(reader.getFieldName());
    }
}
