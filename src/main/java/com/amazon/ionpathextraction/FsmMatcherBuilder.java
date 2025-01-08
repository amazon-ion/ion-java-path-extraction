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

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ionpathextraction.internal.Annotations;
import com.amazon.ionpathextraction.pathcomponents.Index;
import com.amazon.ionpathextraction.pathcomponents.PathComponent;
import com.amazon.ionpathextraction.pathcomponents.Text;
import com.amazon.ionpathextraction.pathcomponents.Wildcard;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Builds a root FsmMatcher for a set of SearchPaths.
 * <br>
 * One key principle in the implementation is to close over as much branching as possible at build time.
 * For example: for a case-insensitive field lookup, lower case the field names once, at build time.
 * <br>
 * The second key principle is that there should be at-most-one Matcher state for a given reader context.
 * So any combination of different paths which could both be active for the same reader context are disallowed.
 * For example: allowing a mix of field names and ordinal positions for a given sub-path.
 * <br>
 * Beyond that, there are some usage patterns which could be included, such as annotations filtering on
 * field names or ordinals, but for which there was no observed usage.
 */
class FsmMatcherBuilder<T> {
    private final PathTreeNode root = new PathTreeNode();
    private final boolean caseInsensitiveAll;
    private final boolean caseInsensitiveFields;

    FsmMatcherBuilder(final boolean caseInsensitiveAll, final boolean caseInsensitiveFields) {
        this.caseInsensitiveAll = caseInsensitiveAll;
        this.caseInsensitiveFields = caseInsensitiveFields;
    }

    /**
     * Incorporate the searchPath into the matcher tree to be built.
     *
     * @throws UnsupportedPathExpression if the SearchPath is not supported.
     */
    void accept(final SearchPath<T> searchPath) {
        List<PathComponent> steps = searchPath.getNormalizedPath();
        PathTreeNode currentNode = root;
        for (PathComponent step : steps) {
            currentNode = currentNode.acceptStep(step);
        }
        currentNode.setCallback(searchPath.getCallback());
    }

    /**
     * Build the FsmMatcher for the set of paths.
     *
     * @throws UnsupportedPathExpression if the combination of SearchPaths is not supported.
     */
    FsmMatcher<T> build() {
        return root.buildMatcher();
    }

    /**
     * Mutable builder node to model the path tree before building into a FsmMatcher.
     */
    private class PathTreeNode {
        BiFunction<IonReader, T, Integer> callback;
        PathTreeNode wildcard;
        Map<Annotations, PathTreeNode> annotatedSplats = new HashMap<>();
        Map<String, PathTreeNode> fields = new HashMap<>();
        Map<Integer, PathTreeNode> indexes = new HashMap<>();

        /**
         * Find or create a new PathTreeNode for the child step.
         *
         * @return the new or existing node.
         * @throws UnsupportedPathExpression if the step contains path components that are not supported
         */
        private PathTreeNode acceptStep(final PathComponent step) {
            if (step.hasAnnotations() && caseInsensitiveAll) {
                throw new UnsupportedPathExpression(
                        "Case Insensitive Matching of Annotations is not yet supported by this matcher.\n"
                                + "Use the legacy matcher or the withMatchFieldNamesCaseInsensitive option instead.");
            }

            PathTreeNode child;
            if (step instanceof Wildcard) {
                if (step.hasAnnotations()) {
                    child = annotatedSplats.computeIfAbsent(step.getAnnotations(), a -> new PathTreeNode());
                } else {
                    if (wildcard == null) {
                        wildcard = new PathTreeNode();
                    }
                    child = wildcard;
                }
            } else {
                if (step.hasAnnotations()) {
                    // this is not too bad to do, but it takes care to do without impacting the non-annotated case
                    // which is the majority of usage. one would also want to mind the principle to avoid multiple
                    // distinct match paths for a given reader context and only allow either annotated or not
                    // for a given field name or index ordinal.
                    throw new UnsupportedPathExpression("Annotations are only supported on wildcards!");
                }

                if (step instanceof Text) {
                    String fieldName = caseInsensitiveFields
                            ? ((Text) step).getFieldName().toLowerCase()
                            : ((Text) step).getFieldName();
                    child = fields.computeIfAbsent(fieldName, f -> new PathTreeNode());
                } else if (step instanceof Index) {
                    child = indexes.computeIfAbsent(((Index) step).getOrdinal(), i -> new PathTreeNode());
                } else {
                    throw new IllegalArgumentException("step of unknown runtime type: " + step.getClass());
                }
            }
            return child;
        }

        private void setCallback(final BiFunction<IonReader, T, Integer> callback) {
            if (this.callback == null) {
                this.callback = callback;
            } else {
                // this would actually be pretty simple to do: just create a ComposedCallback of BiFunctions.
                throw new UnsupportedPathExpression("Cannot set multiple callbacks for same path!");
            }
        }

        private FsmMatcher<T> buildMatcher() {
            List<FsmMatcher<T>> matchers = new ArrayList<>();
            if (wildcard != null) {
                matchers.add(new SplatMatcher<>(wildcard.buildMatcher(), callback));
            }
            if (!annotatedSplats.isEmpty()) {
                List<FsmMatcher<T>> children = new ArrayList<>(annotatedSplats.size());
                List<String[]> annotations = new ArrayList<>(annotatedSplats.size());
                for (Map.Entry<Annotations, PathTreeNode> entry : annotatedSplats.entrySet()) {
                    children.add(entry.getValue().buildMatcher());
                    annotations.add(entry.getKey().getAnnotations());
                }
                matchers.add(new AnnotationsMatcher<>(annotations, children));
            }
            if (!fields.isEmpty()) {
                Map<String, FsmMatcher<T>> children = fields.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, (e) -> e.getValue().buildMatcher()));
                FsmMatcher<T> fieldMatcher = caseInsensitiveFields
                        ? new CaseInsensitiveFieldMatcher<>(children, callback)
                        : new FieldMatcher<>(children, callback);
                matchers.add(fieldMatcher);
            }
            if (!indexes.isEmpty()) {
                Map<Integer, FsmMatcher<T>> children = indexes.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, (e) -> e.getValue().buildMatcher()));
                matchers.add(new IndexMatcher<>(children, callback));
            }

            if (matchers.isEmpty()) {
                return new TerminalMatcher<>(callback);
            } else if (matchers.size() == 1) {
                return matchers.get(0);
            } else {
                // the main issue with allowing more than one is that it means that any given match context
                // may produce multiple matches, and search path writers may become reliant on the order
                // in which callbacks for such cases are called. And in the general case, that might mean
                // a crazy mix between the different types of matching, which devolves to the for-each loop
                // we see in the PathExtractorImpl.
                // That seems like a lot of complexity for a usage pattern of questionable value.
                // So if you're reading this, and you think "oh this is a silly restriction", then take
                // the time to understand why it's important to the path writer and reconsider accordingly.
                throw new UnsupportedPathExpression(
                        "Only one variant of wildcard, annotated wildcard, field names, or ordinals is supported!");
            }
        }
    }

    private static class SplatMatcher<T> extends FsmMatcher<T> {
        FsmMatcher<T> child;

        SplatMatcher(
                final FsmMatcher<T> child,
                final BiFunction<IonReader, T, Integer> callback) {
            this.child = child;
            this.callback = callback;
        }

        @Override
        FsmMatcher<T> transition(final String fieldName, final int position, final Supplier<String[]> annotations) {
            return child;
        }
    }

    private static class FieldMatcher<T> extends FsmMatcher<T> {
        Map<String, FsmMatcher<T>> fields;

        FieldMatcher(
                final Map<String, FsmMatcher<T>> fields,
                final BiFunction<IonReader, T, Integer> callback) {
            this.fields = fields;
            this.callback = callback;
        }

        @Override
        Transitionable transitionsFrom(final IonType ionType) {
            if (ionType == IonType.STRUCT) {
                return Transitionable.POSSIBLE;
            }
            if (ionType == IonType.NULL) {
                return Transitionable.TERMINAL;
            }
            return Transitionable.MISTYPED;
        }

        @Override
        FsmMatcher<T> transition(final String fieldName, final int position, final Supplier<String[]> annotations) {
            return fields.get(fieldName);
        }
    }

    private static class CaseInsensitiveFieldMatcher<T> extends FieldMatcher<T> {
        CaseInsensitiveFieldMatcher(
                final Map<String, FsmMatcher<T>> fields,
                final BiFunction<IonReader, T, Integer> callback) {
            super(fields, callback);
        }

        @Override
        FsmMatcher<T> transition(final String fieldName, final int position, final Supplier<String[]> annotations) {
            return fields.get(fieldName.toLowerCase());
        }
    }

    private static class IndexMatcher<T> extends FsmMatcher<T> {
        Map<Integer, FsmMatcher<T>> indexes;

        IndexMatcher(
                final Map<Integer, FsmMatcher<T>> indexes,
                final BiFunction<IonReader, T, Integer> callback) {
            this.indexes = indexes;
            this.callback = callback;
        }

        @Override
        FsmMatcher<T> transition(final String fieldName, final int position, final Supplier<String[]> annotations) {
            return indexes.get(position);
        }
    }

    private static class TerminalMatcher<T> extends FsmMatcher<T> {
        TerminalMatcher(final BiFunction<IonReader, T, Integer> callback) {
            this.callback = callback;
        }

        @Override
        Transitionable transitionsFrom(final IonType ionType) {
            return Transitionable.TERMINAL;
        }

        @Override
        FsmMatcher<T> transition(final String fieldName, final int position, final Supplier<String[]> annotations) {
            return null;
        }
    }

    private static class AnnotationsMatcher<T> extends FsmMatcher<T> {
        List<String[]> candidates;
        List<FsmMatcher<T>> matchers;

        AnnotationsMatcher(final List<String[]> candidates, final List<FsmMatcher<T>> matchers) {
            this.candidates = candidates;
            this.matchers = matchers;
        }

        @Override
        FsmMatcher<T> transition(final String fieldName, final int position, final Supplier<String[]> annotations) {
            for (int i = 0; i < candidates.size(); i++) {
                if (Arrays.equals(candidates.get(i), annotations.get())) {
                    return matchers.get(i);
                }
            }
            return null;
        }
    }
}
