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
import static software.amazon.com.ionpathextraction.utils.Preconditions.checkState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import software.amazon.com.ionpathextraction.pathcomponents.PathComponent;
import software.amazon.ion.IonReader;

/**
 * <p>
 *     Default implementation of {@link PathExtractor}.
 * </p>
 * <p>
 *     <strong>WARNING:</strong> not Thread safe.
 * </p>
 */
class PathExtractorImpl implements PathExtractor {

    private final PathExtractorConfig config;
    private final Tracker tracker;

    private final List<SearchPath> searchPaths;
    private final List<Function<IonReader, Integer>> callbacks;

    /**
     * Constructor, should only be invoked by {@link PathExtractorBuilder}.
     */
    PathExtractorImpl(final List<SearchPath> searchPaths,
                      final List<Function<IonReader, Integer>> callbacks,
                      final PathExtractorConfig config) {

        this.searchPaths = searchPaths;
        this.callbacks = callbacks;
        this.config = config;

        int size = searchPaths.stream()
            .mapToInt(SearchPath::getId)
            .max()
            .orElse(0);

        tracker = new Tracker(size);
    }

    @Override
    public void match(final IonReader reader) {
        checkArgument(reader.getDepth() == 0 || config.isMatchRelativePaths(),
            "reader must be at depth zero, it was at:" + reader.getDepth());

        // marks all search paths as active
        tracker.reset(searchPaths);
        matchRecursive(reader);
    }

    private int matchRecursive(final IonReader reader) {
        final int currentDepth = tracker.getCurrentDepth();
        int ordinal = 0;

        while (reader.next() != null) {
            // will continue to next depth
            final List<SearchPath> partialMatches = new ArrayList<>();

            boolean hasTerminalMatch = false;
            for (SearchPath sp : tracker.activePaths()) {
                boolean match = pathComponentMatches(sp, reader, ordinal);
                boolean isTerminal = isTerminal(sp);

                if (match && isTerminal) {
                    hasTerminalMatch = true;
                    int stepOutTimes = invokeCallback(reader, sp);
                    if (stepOutTimes > 0) {
                        return stepOutTimes - 1;
                    }
                }

                if (!isTerminal) {
                    // all non terminal paths are partial pathComponentMatches at depth zero
                    if (currentDepth == 0) {
                        partialMatches.add(sp);
                    } else if (match) {
                        partialMatches.add(sp);
                    }
                }
            }

            if (needsToStepIn(reader, hasTerminalMatch)) {
                tracker.push(partialMatches);
                reader.stepIn();
                int stepOutTimes = matchRecursive(reader);
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

    private int invokeCallback(final IonReader reader, final SearchPath searchPath) {
        int previousReaderDepth = reader.getDepth();
        int stepOutTimes = callbacks.get(searchPath.getId()).apply(reader);
        int newReaderDepth = reader.getDepth();

        checkState(previousReaderDepth == newReaderDepth,
            "Reader must be at same depth when returning from callbacks. initial: "
                + previousReaderDepth
                + ", new: "
                + newReaderDepth);

        return stepOutTimes;
    }

    private boolean needsToStepIn(final IonReader reader, final boolean hasTerminalMatches) {
        if (tracker.getCurrentDepth() == 0 && hasTerminalMatches) {
            return false;
        }

        switch (reader.getType()) {
            case LIST:
            case SEXP:
            case STRUCT:
            case DATAGRAM:
                return true;
        }

        return false;
    }

    private boolean pathComponentMatches(final SearchPath searchPath,
                                         final IonReader reader,
                                         final int currentPosition) {
        // depth 0 can only match the empty search path: ()
        int depth = tracker.getCurrentDepth();
        List<PathComponent> pathComponents = searchPath.getPathComponents();

        if (depth == 0) {
            return pathComponents.isEmpty();
        } else if (depth <= pathComponents.size()) {
            return pathComponents.get(depth - 1).matches(reader, currentPosition, config);
        }

        return false;
    }

    private boolean isTerminal(final SearchPath searchPath) {
        return tracker.getCurrentDepth() == searchPath.getPathComponents().size();
    }

    private static class Tracker {

        private final Deque<List<SearchPath>> stack;

        Tracker(final int size) {
            stack = new ArrayDeque<>(size);
        }

        void reset(final List<SearchPath> searchPaths) {
            stack.clear();
            stack.push(searchPaths);
        }

        List<SearchPath> activePaths() {
            return stack.peek();
        }

        int getCurrentDepth() {
            return stack.size() - 1;
        }

        void push(final List<SearchPath> partialMatches) {
            stack.push(partialMatches);
        }

        void pop() {
            stack.pop();
        }
    }
}
