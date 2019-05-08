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

package software.amazon.ionpathextraction;

import com.amazon.ion.IonReader;
import java.util.List;
import java.util.function.BiFunction;
import software.amazon.ionpathextraction.internal.Annotations;
import software.amazon.ionpathextraction.internal.MatchContext;
import software.amazon.ionpathextraction.pathcomponents.PathComponent;

/**
 * A path which is provided to the extractor for matching.
 *
 * @param <T> type accepted by the callback function
 */
final class SearchPath<T> {

    private final List<PathComponent> pathComponents;
    private final BiFunction<IonReader, T, Integer> callback;
    private final Annotations annotations;

    SearchPath(final List<PathComponent> pathComponents,
               final BiFunction<IonReader, T, Integer> callback,
               final Annotations annotations) {
        this.annotations = annotations;
        this.pathComponents = pathComponents;
        this.callback = callback;
    }

    /**
     * Number of path components in this search path.
     */
    int size() {
        return pathComponents.size();
    }

    /**
     * Callback to be invoked when the Search Path is matched.
     */
    BiFunction<IonReader, T, Integer> getCallback() {
        return callback;
    }

    /**
     * Checks that this search path matches the stream at a given path context index.
     */
    boolean partialMatchAt(final MatchContext context) {
        int pathComponentIndex = context.getPathComponentIndex();

        if (pathComponentIndex == 0) {
            return annotations.match(context.getAnnotations(), context.getConfig().isMatchCaseInsensitive());
        } else if (pathComponentIndex <= pathComponents.size()) {
            return pathComponents.get(pathComponentIndex - 1).matches(context);
        }

        return false;
    }
}