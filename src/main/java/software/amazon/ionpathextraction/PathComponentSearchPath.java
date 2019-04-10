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
import software.amazon.ionpathextraction.pathcomponents.PathComponent;

/**
 * A search path that has at least one path component, e.g. (*)
 *
 * @param <T> type accepted by the callback function
 */
final class PathComponentSearchPath<T> implements SearchPath<T> {

    private final List<PathComponent> pathComponents;
    private final BiFunction<IonReader, T, Integer> callback;

    PathComponentSearchPath(final List<PathComponent> pathComponents,
                            final BiFunction<IonReader, T, Integer> callback) {
        this.pathComponents = pathComponents;
        this.callback = callback;
    }

    List<PathComponent> getPathComponents() {
        return pathComponents;
    }

    public BiFunction<IonReader, T, Integer> getCallback() {
        return callback;
    }

    @Override
    public Type getType() {
        return Type.PATH_COMPONENTS;
    }
}