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

/**
 * <p>
 * Path extractor takes registered paths and when it finds one during stream processing invokes the respective callback.
 * This allows the Ion reader to plan the most efficient traversal over the data without requiring further manual
 * interaction from the user.
 * </p>
 *
 * <p>
 * For example, there is no reason to step in to containers which could not possibly match one of the search paths. When
 * encoded in binary Ion, the resulting skip is a seek forward in the input stream, which is inexpensive relative to the
 * cost of parsing (and in the case of a DOM, materializing) the skipped value.
 * </p>
 *
 * <p>
 * <strong>WARNING:</strong>Implementations of this interface are not required to be Thread safe
 * </p>
 */
public interface PathExtractor<T> {

    /**
     * Iterates over the reader looking for registered search paths, when a match is found invokes the respective
     * callback.
     *
     * @param reader {@link IonReader} to process.
     */
    void match(final IonReader reader);

    /**
     * Iterates over the reader looking for registered search paths, when a match is found invokes the respective
     * callback.
     *
     * @param reader {@link IonReader} to process.
     * @param context context passed in to callback functions.
     */
    void match(final IonReader reader, final T context);
}
