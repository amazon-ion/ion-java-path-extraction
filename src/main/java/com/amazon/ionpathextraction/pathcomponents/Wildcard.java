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

import com.amazon.ionpathextraction.internal.Annotations;
import com.amazon.ionpathextraction.internal.MatchContext;

/**
 * Wildcard path component matches any value, example.
 * <pre>
 * data: {foo: [1,2,3], bar: { baz: [1] }}
 *
 * search path | callback invoked with reader at
 * ------------|--------------------
 *  (*)        | [1, 2, 3] and { baz: [1] }
 *  (* *)      | 1, 2, 3 and [1]
 * </pre>
 */
public final class Wildcard extends PathComponent {

    public static final String TEXT = "*";

    public Wildcard(final String[] annotations) {
        super(new Annotations(annotations));
    }

    @Override
    public boolean innerMatches(final MatchContext context) {
        return true;
    }
}
