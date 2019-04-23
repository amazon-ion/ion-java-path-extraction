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

import static software.amazon.ionpathextraction.internal.ArrayUtils.arrayEquals;

import software.amazon.ionpathextraction.internal.MatchContext;

/**
 * Annotated path component combines with other path components to match only annotated values.
 * <pre>
 * data: 1 2 3 annot::4 {foo: 10, foo: annot::20}
 *
 * search path   | callback invoked with reader at
 * --------------|--------------------
 *  (annot::*)   | [4]
 *  (*)          | [1, 2, 3, 4]
 *  (annot::foo) | [20]
 *  (foo)        | [10, 20]
 * </pre>
 */
public final class AnnotatedPathComponent implements PathComponent {

    private final String[] annotations;
    private final PathComponent pathComponent;

    public AnnotatedPathComponent(final String[] annotations, final PathComponent pathComponent) {
        this.annotations = annotations;
        this.pathComponent = pathComponent;
    }

    @Override
    public boolean matches(final MatchContext context) {
        return arrayEquals(context.getAnnotations(), annotations, context.getConfig().isMatchCaseInsensitive())
            && pathComponent.matches(context);
    }
}
