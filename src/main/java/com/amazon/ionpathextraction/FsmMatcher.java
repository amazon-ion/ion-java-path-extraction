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

package com.amazon.ionpathextraction;

import com.amazon.ion.IonReader;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Base class for match states in the Finite State Machine matching implementation.
 */
abstract class FsmMatcher<T> {
    /**
     * Callback for match state. May be null.
     */
    BiFunction<IonReader, T, Integer> callback;

    /**
     * Indicates there are no possible child transitions.
     */
    boolean terminal = false;

    /**
     * Return the child matcher for the given reader context.
     * Return null if there is no match.
     * <br>
     * @param position will be -1 for top-level-values, otherwise will be the position ordinal
     *         of the value in the container, both for sequences and structs.
     * @param fieldName will be non-null only for struct values.
     */
    abstract FsmMatcher<T> transition(String fieldName, int position, Supplier<String[]> annotations);
}
