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

package com.amazon.ionpathextraction.internal;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * <p>
 * Represents the optional annotation that path components or search paths should match on.
 * </p>
 *
 * <p>
 * Internal only. Not intended for application use.
 * </p>
 */
public final class Annotations {

    public static final Annotations EMPTY = new Annotations(new String[] {});

    final String[] rawAnnotations;

    /**
     * Constructor.
     */
    public Annotations(final String[] rawAnnotations) {
        this.rawAnnotations = rawAnnotations;
    }

    public String[] getAnnotations() {
        return rawAnnotations;
    }

    public boolean hasAnnotations() {
        return rawAnnotations.length > 0;
    }

    /**
     * returns true if it matches on the annotations provided.
     */
    public boolean match(final String[] annotations, final boolean ignoreCase) {
        return rawAnnotations.length == 0
            || arrayEquals(rawAnnotations, annotations, ignoreCase);
    }

    private static boolean arrayEquals(final String[] left, final String[] right, final boolean ignoreCase) {
        if (left.length != right.length) {
            return false;
        }

        return IntStream.range(0, left.length)
            .allMatch(i -> ignoreCase ? left[i].equalsIgnoreCase(right[i]) : left[i].equals(right[i]));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Annotations)) {
            return false;
        }
        return Arrays.equals(rawAnnotations, ((Annotations) o).rawAnnotations);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(rawAnnotations);
    }
}
