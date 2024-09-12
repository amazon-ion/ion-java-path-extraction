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

import com.amazon.ionpathextraction.exceptions.PathExtractionException;

/**
 * <p>
 * Precondition check helper.
 * </p>
 *
 * <p>
 * Internal only. Not intended for application use.
 * </p>
 */
public class Preconditions {

    /**
     * Validates argument, fails if condition is not met.
     * Prefer variable arity overload instead of concatenating Strings at call-site!!!
     *
     * @param isValid if condition is met.
     * @param message error message.
     * @throws PathExtractionException if not valid.
     */
    public static void checkArgument(final boolean isValid, final String message) {
        if (!isValid) {
            throw new PathExtractionException(message);
        }
    }

    /**
     * Validates argument, fails if condition is not met.
     * Prefer variable arity overload instead of concatenating Strings at call-site!!!
     *
     * @param isValid if condition is met.
     * @param messageFormat error message _format_.
     * @param args arguments to String.format()
     * @throws PathExtractionException if not valid.
     */
    public static void checkArgument(final boolean isValid, final String messageFormat, final Object... args) {
        if (!isValid) {
            throw new PathExtractionException(String.format(messageFormat, args));
        }
    }

    /**
     * Validates a state, fails if condition is not met.
     *
     * @param isValid if condition is met.
     * @param message error message.
     * @throws PathExtractionException if not valid.
     */
    public static void checkState(final boolean isValid, final String message) {
        if (!isValid) {
            throw new PathExtractionException(message);
        }
    }

    /**
     * Validates a state, fails if condition is not met.
     *
     * @param isValid if condition is met.
     * @param messageFormat error message _format_.
     * @param args arguments to String.format()
     * @throws PathExtractionException if not valid.
     */
    public static void checkState(final boolean isValid, final String messageFormat, final Object... args) {
        if (!isValid) {
            throw new PathExtractionException(String.format(messageFormat, args));
        }
    }
}
