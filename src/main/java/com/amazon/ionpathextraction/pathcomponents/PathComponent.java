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

package com.amazon.ionpathextraction.pathcomponents;

import static com.amazon.ionpathextraction.internal.Preconditions.checkArgument;

import com.amazon.ionpathextraction.internal.Annotations;
import com.amazon.ionpathextraction.internal.MatchContext;

/**
 * A search path component, for example the path (foo * 1) has three components.
 *
 * <ol>
 * <li>foo</li>
 * <li>*</li>
 * <li>1</li>
 * </ol>
 */
public abstract class PathComponent {

    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    protected final Annotations annotations;

    PathComponent(final Annotations annotations) {
        checkArgument(annotations != null, "annotations cannot be null");

        this.annotations = annotations;
    }

    public Annotations getAnnotations() {
        return annotations;
    }

    public boolean hasAnnotations() {
        return annotations.hasAnnotations();
    }

    /**
     * Checks if this component matches the current reader position with the given configuration.
     *
     * @return true if the component matches the current reader position false otherwise.
     */
    public final boolean matches(final MatchContext context) {
        return annotations.match(context.getAnnotations(), context.getConfig().isMatchCaseInsensitive())
            && innerMatches(context);
    }

    /**
     * Called by {@link PathComponent#matches(MatchContext)} after applying the standard matching logic. Subclasses must
     * implement their specific matching logic in this method.
     */
    protected abstract boolean innerMatches(final MatchContext context);
}
