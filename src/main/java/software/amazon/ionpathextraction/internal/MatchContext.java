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

package software.amazon.ionpathextraction.internal;

import com.amazon.ion.IonReader;

/**
 * <p>
 * Context for matching search paths.
 * </p>
 *
 * <p>
 * Internal only. Not intended for application use.
 * </p>
 */
public class MatchContext {
    private final IonReader reader;
    private final int pathComponentIndex;
    private final int readerContainerIndex;
    private final String[] annotations;
    private final PathExtractorConfig config;

    /**
     * Constructor.
     */
    public MatchContext(final IonReader reader,
                        final int pathComponentIndex,
                        final int readerContainerIndex,
                        final PathExtractorConfig config) {
        this.reader = reader;
        this.pathComponentIndex = pathComponentIndex;
        this.readerContainerIndex = readerContainerIndex;
        this.annotations = reader.getTypeAnnotations();
        this.config = config;
    }

    public IonReader getReader() {
        return reader;
    }

    public int getPathComponentIndex() {
        return pathComponentIndex;
    }

    public int getReaderContainerIndex() {
        return readerContainerIndex;
    }

    public String[] getAnnotations() {
        return annotations;
    }

    public PathExtractorConfig getConfig() {
        return config;
    }
}
