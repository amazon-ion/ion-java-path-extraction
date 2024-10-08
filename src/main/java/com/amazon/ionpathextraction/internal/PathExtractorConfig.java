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

/**
 * Internal only. Not intended for application use.
 */
public final class PathExtractorConfig {

    private final boolean matchRelativePaths;
    private final boolean matchCaseInsensitive;
    private final boolean matchFieldsCaseInsensitive;

    /**
     * Instantiate a PathExtractorConfig.
     */
    public PathExtractorConfig(
            final boolean matchRelativePaths,
            final boolean matchCaseInsensitive,
            final boolean matchFieldsCaseInsensitive) {
        this.matchRelativePaths = matchRelativePaths;
        this.matchCaseInsensitive = matchCaseInsensitive;
        this.matchFieldsCaseInsensitive = matchFieldsCaseInsensitive;
    }

    public boolean isMatchRelativePaths() {
        return matchRelativePaths;
    }

    public boolean isMatchCaseInsensitive() {
        return matchCaseInsensitive;
    }

    public boolean isMatchFieldsCaseInsensitive() {
        return matchFieldsCaseInsensitive;
    }
}
