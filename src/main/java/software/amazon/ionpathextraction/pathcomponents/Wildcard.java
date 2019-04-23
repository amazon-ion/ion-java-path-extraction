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

import software.amazon.ionpathextraction.internal.MatchContext;

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
public final class Wildcard implements PathComponent {

    public static final String TEXT = "*";

    /**
     * Singleton {@link Wildcard} instance.
     */
    public static final Wildcard INSTANCE = new Wildcard();

    /**
     * use INSTANCE.
     */
    private Wildcard() {
    }

    @Override
    public boolean matches(final MatchContext context) {
        return true;
    }
}
