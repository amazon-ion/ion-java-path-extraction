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

package software.amazon.ionpathextraction.internal;

import java.util.stream.IntStream;

/**
 * <p>
 * Utility methods for dealing with arrays.
 * </p>
 *
 * <p>
 * Internal only. Not intended for application use.
 * </p>
 */
public class ArrayUtils {

    /**
     * Checks if two string arrays are equals.
     *
     * @param left left array to compare
     * @param right right array
     * @param ignoreCase if it ignores case when comparing elements
     * @return true if the arrays are equals false otherwise
     */
    public static boolean arrayEquals(final String[] left, final String[] right, final boolean ignoreCase) {
        if (left.length != right.length) {
            return false;
        }

        return IntStream.range(0, left.length)
            .allMatch(i -> ignoreCase ? left[i].equalsIgnoreCase(right[i]) : left[i].equals(right[i]));
    }
}
