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

import static com.amazon.ionpathextraction.internal.Preconditions.checkArgument;
import static com.amazon.ionpathextraction.internal.Preconditions.checkState;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ionpathextraction.exceptions.PathExtractionException;
import com.amazon.ionpathextraction.internal.PathExtractorConfig;
import java.util.List;
import java.util.function.BiFunction;

/**
 * A PathExtractor modeled as a Finite State Machine.
 * <br>
 * Compared to the PathExtractorImpl, this supports a narrower set of
 * SearchPaths and their combinations, but is more performant, particularly
 * when a large number of field names are searched for.
 * <br>
 * A more comprehensive explanation of the strictness from a user PoV can be
 * found on the PathExtractorBuilder.buildStrict() API method. Notes on the
 * 'why' can be found in the FsmMatcherBuilder.
 */
class FsmPathExtractor<T> implements PathExtractor<T> {
    private final FsmMatcher<T> rootMatcher;
    private final PathExtractorConfig config;

    private FsmPathExtractor(
            final FsmMatcher<T> rootMatcher,
            final PathExtractorConfig config) {
        this.rootMatcher = rootMatcher;
        this.config = config;
    }

    static <U> FsmPathExtractor<U> create(
            final List<SearchPath<U>> searchPaths,
            final PathExtractorConfig config) {
        FsmMatcherBuilder<U> builder = new FsmMatcherBuilder<>(
                config.isMatchCaseInsensitive(),
                config.isMatchFieldsCaseInsensitive());
        for (SearchPath<U> path : searchPaths) {
            builder.accept(path);
        }

        return new FsmPathExtractor<>(builder.build(), config);
    }

    @Override
    public void match(final IonReader reader) {
        match(reader, null);
    }

    @Override
    public void match(final IonReader reader, final T context) {
        checkArgument(reader.getDepth() == 0 || config.isMatchRelativePaths(),
                "reader must be at depth zero, it was at: %s", reader.getDepth());

        while (reader.next() != null) {
            matchCurrentValue(reader, context);
        }
    }

    @Override
    public void matchCurrentValue(final IonReader reader) {
        matchCurrentValue(reader, null);
    }

    @Override
    public void matchCurrentValue(final IonReader reader, final T context) {
        checkArgument(reader.getDepth() == 0 || config.isMatchRelativePaths(),
                "reader must be at depth zero, it was at: %s", reader.getDepth());
        checkArgument(reader.getType() != null,
                "reader must be positioned at a value; call IonReader.next() first.");

        matchRecursive(reader, rootMatcher, context, -1, reader.getDepth());
    }

    private int matchRecursive(
            final IonReader reader,
            final FsmMatcher<T> matcher,
            final T context,
            final int position,
            final int initialDepth) {
        FsmMatcher<T> child = matcher.transition(reader.getFieldName(), position, reader::getTypeAnnotations);
        if (child == null) {
            return 0;
        }

        if (child.callback != null) {
            int stepOut = invokeCallback(reader, child.callback, initialDepth, context);
            if (stepOut > 0) {
                return stepOut;
            }
        }

        if (IonType.isContainer(reader.getType()) && !child.terminal) {
            reader.stepIn();
            int childPos = 0;
            int stepOut = 0;
            while (stepOut == 0 && reader.next() != null) {
                stepOut = matchRecursive(reader, child, context, childPos++, initialDepth);
            }
            reader.stepOut();
            if (stepOut > 0) {
                return stepOut - 1;
            }
        }

        return 0;
    }

    private int invokeCallback(
            final IonReader reader,
            final BiFunction<IonReader, T, Integer> callback,
            final int initialReaderDepth,
            final T context) {
        int previousReaderDepth = reader.getDepth();

        int stepOutTimes = callback.apply(reader, context);
        int newReaderDepth = reader.getDepth();

        checkState(previousReaderDepth == newReaderDepth,
                    "Reader must be at same depth when returning from callbacks. initial: %s, new: %s",
                    previousReaderDepth,
                    newReaderDepth);

        // we don't allow users to step out the initial reader depth
        int readerRelativeDepth = reader.getDepth() - initialReaderDepth;

        checkState(stepOutTimes <= readerRelativeDepth,
                    STEP_OUT_TOO_FAR_MSG,
                    stepOutTimes,
                    readerRelativeDepth);

        return stepOutTimes;
    }

    private static final String STEP_OUT_TOO_FAR_MSG =
        "Callback return cannot be greater than the reader current relative depth. "
        + "return: %s, relative reader depth: %s";
}
