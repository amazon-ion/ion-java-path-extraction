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

package software.amazon.com.ionpathextraction;

import static software.amazon.com.ionpathextraction.utils.Preconditions.checkArgument;

import java.util.List;
import java.util.function.Function;
import software.amazon.com.ionpathextraction.pathcomponents.PathComponent;
import software.amazon.ion.IonReader;

// FIXME this is a weird class, probably better to remove it

/**
 * A path which is provided to the extractor for matching.
 */
class SearchPath {

    private final int id;
    private final List<PathComponent> pathComponents;

    SearchPath(final int id, final List<PathComponent> pathComponents) {
        this.id = id;
        this.pathComponents = pathComponents;
    }

    int getId() {
        return id;
    }

    List<PathComponent> getPathComponents() {
        return pathComponents;
    }
}
