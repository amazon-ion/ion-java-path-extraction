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

import static software.amazon.ionpathextraction.utils.Preconditions.checkArgument;
import static software.amazon.ionpathextraction.utils.Preconditions.checkState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import software.amazon.ion.IonReader;
import software.amazon.ion.IonType;
import software.amazon.ionpathextraction.pathcomponents.PathComponent;

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
            .mapToInt(sp -> sp.getPathComponents().size())
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

        matchRecursive(reader, tracker, context);
    }

    private int matchRecursive(final IonReader reader, final Tracker<T> tracker, final T context) {
        final int currentDepth = tracker.getCurrentDepth();
        int ordinal = 0;

        while (reader.next() != null) {
            // will continue to next depth
            final List<SearchPath<T>> partialMatches = new ArrayList<>();

            for (SearchPath<T> sp : tracker.activePaths()) {
                boolean match = pathComponentMatches(sp, reader, tracker.getCurrentDepth(), ordinal);
                boolean isTerminal = isTerminal(tracker.getCurrentDepth(), sp);

                if (match && isTerminal) {
                    int stepOutTimes = invokeCallback(reader, sp, tracker.getInitialReaderDepth(), context);
                    if (stepOutTimes > 0) {
                        return stepOutTimes - 1;
                    }
                }

                // all non terminal paths are partial matches at depth zero
                if (!isTerminal && (currentDepth == 0 || match)) {
                    partialMatches.add(sp);
                }
            }

            if (IonType.isContainer(reader.getType()) && !partialMatches.isEmpty()) {
                tracker.push(partialMatches);
                reader.stepIn();
                int stepOutTimes = matchRecursive(reader, tracker, context);
                reader.stepOut();
                tracker.pop();

                if (stepOutTimes > 0) {
                    return stepOutTimes - 1;
                }
            }

            ordinal += 1;
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

    private boolean pathComponentMatches(final SearchPath<T> searchPath,
                                         final IonReader reader,
                                         final int currentDepth,
                                         final int currentPosition) {
        List<PathComponent> pathComponents = searchPath.getPathComponents();

        // currentDepth 0 can only match the empty search path: ()
        if (currentDepth == 0) {
            return pathComponents.isEmpty();
        } else if (currentDepth <= pathComponents.size()) {
            return pathComponents.get(currentDepth - 1).matches(reader, currentPosition, config);
        }

        return false;
    }

    private boolean isTerminal(final int currentDepth, final SearchPath searchPath) {
        return currentDepth == searchPath.getPathComponents().size();
    }

    private static class Tracker<T> {

        private final Deque<List<SearchPath<T>>> stack;
        private int initialReaderDepth;

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
