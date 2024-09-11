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
import com.amazon.ionpathextraction.internal.MatchContext;
import com.amazon.ionpathextraction.internal.PathExtractorConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * <p>
 * Default implementation of {@link PathExtractor}.
 * </p>
 * <p>
 * This implementation is thread safe.
 * </p>
 */
final class PathExtractorImpl<T> implements PathExtractor<T> {

    private final PathExtractorConfig config;
    private final List<SearchPath<T>> searchPaths;

    private final int maxSearchPathDepth;

    /**
     * Constructor, should only be invoked by {@link PathExtractorBuilder}.
     */
    PathExtractorImpl(final List<SearchPath<T>> searchPaths,
                      final PathExtractorConfig config) {

        this.searchPaths = searchPaths;
        this.config = config;

        maxSearchPathDepth = searchPaths.stream()
            .mapToInt(SearchPath::size)
            .max()
            .orElse(0);
    }

    @Override
    public void match(final IonReader reader) {
        match(reader, null);
    }

    @Override
    public void match(final IonReader reader, final T context) {
        checkArgument(reader.getDepth() == 0 || config.isMatchRelativePaths(),
            "reader must be at depth zero, it was at:" + reader.getDepth());

        // short circuit when there are zero SearchPaths
        if (searchPaths.isEmpty()) {
            return;
        }

        final Tracker<T> tracker = new Tracker<>(maxSearchPathDepth, searchPaths, reader.getDepth());

        matchAllValuesRecursive(reader, tracker, context);
    }

    @Override
    public void matchCurrentValue(final IonReader reader) {
        matchCurrentValue(reader, null);
    }

    @Override
    public void matchCurrentValue(final IonReader reader, final T context) {
        checkArgument(reader.getDepth() == 0 || config.isMatchRelativePaths(),
            "reader must be at depth zero, it was at:" + reader.getDepth());
        checkArgument(reader.getType() != null,
            "reader must be positioned at a value; call IonReader.next() first.");

        // short circuit when there are zero SearchPaths
        if (searchPaths.isEmpty()) {
            return;
        }

        final Tracker<T> tracker = new Tracker<>(maxSearchPathDepth, searchPaths, reader.getDepth());
        matchCurrentValueRecursive(reader, tracker, context, 0, tracker.getCurrentDepth());
    }

    private int matchCurrentValueRecursive(
        final IonReader reader,
        final Tracker<T> tracker,
        final T context,
        final int readerContainerIndex,
        final int currentDepth
    ) {
        // will continue to next depth
        final List<SearchPath<T>> partialMatches = new ArrayList<>();

        final MatchContext matchContext = new MatchContext(reader, currentDepth, readerContainerIndex, config);
        for (SearchPath<T> sp : tracker.activePaths()) {
            // a terminal search path is at the last path component meaning that if this search path partially
            // matches it will be a full match and the callback must be invoked
            boolean searchPathIsTerminal = isTerminal(tracker.getCurrentDepth(), sp);
            boolean partialMatch = sp.partialMatchAt(matchContext);

            if (partialMatch) {
                if (searchPathIsTerminal) {
                    int stepOutTimes = invokeCallback(reader, sp, tracker.getInitialReaderDepth(), context);
                    if (stepOutTimes > 0) {
                        return stepOutTimes;
                    }
                } else {
                    partialMatches.add(sp);
                }
            }
        }

        if (IonType.isContainer(reader.getType()) && !partialMatches.isEmpty()) {
            tracker.push(partialMatches);
            reader.stepIn();
            int stepOutTimes = matchAllValuesRecursive(reader, tracker, context);
            reader.stepOut();
            tracker.pop();

            if (stepOutTimes > 0) {
                return stepOutTimes;
            }
        }
        return 0;
    }

    private int matchAllValuesRecursive(final IonReader reader, final Tracker<T> tracker, final T context) {
        final int currentDepth = tracker.getCurrentDepth();
        int readerContainerIndex = 0;

        while (reader.next() != null) {
            int stepOutTimes = matchCurrentValueRecursive(reader, tracker, context, readerContainerIndex, currentDepth);
            if (stepOutTimes > 0) {
                return stepOutTimes - 1;
            }
            readerContainerIndex += 1;
        }

        return 0;
    }

    private int invokeCallback(final IonReader reader,
                               final SearchPath<T> searchPath,
                               final int initialReaderDepth,
                               final T context) {
        int previousReaderDepth = reader.getDepth();

        int stepOutTimes = searchPath.getCallback().apply(reader, context);
        int newReaderDepth = reader.getDepth();

        checkState(previousReaderDepth == newReaderDepth,
            "Reader must be at same depth when returning from callbacks. initial: "
                + previousReaderDepth
                + ", new: "
                + newReaderDepth);

        // we don't allow users to step out the initial reader depth
        int readerRelativeDepth = reader.getDepth() - initialReaderDepth;

        checkState(stepOutTimes <= readerRelativeDepth,
            "Callback return cannot be greater than the reader current relative depth."
                + " return: "
                + stepOutTimes
                + ", relative reader depth: "
                + readerRelativeDepth);

        return stepOutTimes;
    }

    private boolean isTerminal(final int pathComponentIndex, final SearchPath<T> searchPath) {
        return pathComponentIndex == searchPath.size();
    }

    private static class Tracker<T> {

        private final Deque<List<SearchPath<T>>> stack;
        private final int initialReaderDepth;

        Tracker(final int size, final List<SearchPath<T>> searchPaths, final int initialReaderDepth) {
            stack = new ArrayDeque<>(size);
            stack.push(searchPaths);
            this.initialReaderDepth = initialReaderDepth;
        }

        List<SearchPath<T>> activePaths() {
            return stack.peek();
        }

        int getCurrentDepth() {
            return stack.size() - 1;
        }

        void push(final List<SearchPath<T>> partialMatches) {
            stack.push(partialMatches);
        }

        void pop() {
            stack.pop();
        }

        int getInitialReaderDepth() {
            return initialReaderDepth;
        }
    }
}
