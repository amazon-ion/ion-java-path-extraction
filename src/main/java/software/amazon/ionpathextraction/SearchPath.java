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

import java.util.List;
import java.util.function.BiFunction;
import software.amazon.ion.IonReader;
import software.amazon.ionpathextraction.internal.ArrayUtils;
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
    private final String[] annotations;

    SearchPath(final List<PathComponent> pathComponents,
               final BiFunction<IonReader, T, Integer> callback,
               final String[] annotations) {
        this.annotations = annotations;
        this.pathComponents = pathComponents;
        this.callback = callback;
    }

    int size() {
        return pathComponents.size();
    }

    boolean isTerminal(final int pathComponentIndex) {
        return pathComponentIndex == size();
    }

    boolean partialMatchAt(final MatchContext context) {
        int pathComponentIndex = context.getPathComponentIndex();

        if (pathComponentIndex == 0) {
            return annotationsMatch(context);
        } else if (pathComponentIndex <= pathComponents.size()) {
            return pathComponents.get(pathComponentIndex - 1).matches(context);
        }

        return false;
    }

    BiFunction<IonReader, T, Integer> getCallback() {
        return callback;
    }

    private boolean annotationsMatch(final MatchContext context) {
        if (annotations.length == 0) {
            return true;
        }

        return ArrayUtils.arrayEquals(
            annotations,
            context.getAnnotations(),
            context.getConfig().isMatchCaseInsensitive());
    }
}