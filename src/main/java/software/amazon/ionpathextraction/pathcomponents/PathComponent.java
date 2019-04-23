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

package software.amazon.ionpathextraction.pathcomponents;

import software.amazon.ionpathextraction.internal.MatchContext;

/**
 * A search path component, for example the path (foo * 1) has three components.
 *
 * <ol>
 * <li>foo</li>
 * <li>*</li>
 * <li>1</li>
 * </ol>
 */
public interface PathComponent {

    /**
     * Checks if this component matches the current reader position with the given configuration.
     *
     * @return true if the component matches the current reader position false otherwise.
     */
    boolean matches(final MatchContext context);
}
