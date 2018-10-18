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

package software.amazon.ionpathextraction.utils;

import software.amazon.ionpathextraction.exceptions.PathExtractionException;

/**
 * Precondition check helper.
 */
public class Preconditions {

    /**
     * Validates argument, fails if condition is not met.
     *
     * @param isValid if condition is met.
     * @param message error message.
     * @throws PathExtractionException if not valid.
     */
    public static void checkArgument(final Boolean isValid, final String message) {
        if (!isValid) {
            throw new PathExtractionException(message);
        }
    }

    /**
     * Validates a state, fails if condition is not met.
     *
     * @param isValid if condition is met.
     * @param message error message.
     * @throws PathExtractionException if not valid.
     */
    public static void checkState(final Boolean isValid, final String message) {
        if (!isValid) {
            throw new PathExtractionException(message);
        }
    }
}
