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
import static com.amazon.ionpathextraction.pathcomponents.PathComponent.EMPTY_STRING_ARRAY;

import com.amazon.ion.IonReader;
import com.amazon.ionpathextraction.internal.Annotations;
import com.amazon.ionpathextraction.internal.PathExtractorConfig;
import com.amazon.ionpathextraction.pathcomponents.PathComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * {@link PathExtractor} builder.
 */
public final class PathExtractorBuilder<T> {

    private static final boolean DEFAULT_MATCH_RELATIVE_PATHS = false;
    private static final boolean DEFAULT_CASE_INSENSITIVE = false;

    private final List<SearchPath<T>> searchPaths = new ArrayList<>();
    private boolean matchRelativePaths;
    private boolean matchCaseInsensitive;
    private boolean matchFieldsCaseInsensitive;

    private PathExtractorBuilder() {
    }

    /**
     * Creates a new builder with standard configuration.
     *
     * @return new standard builder instance.
     */
    public static <T> PathExtractorBuilder<T> standard() {
        PathExtractorBuilder<T> builder = new PathExtractorBuilder<>();
        builder.matchCaseInsensitive = DEFAULT_CASE_INSENSITIVE;
        builder.matchRelativePaths = DEFAULT_MATCH_RELATIVE_PATHS;
        builder.matchFieldsCaseInsensitive = DEFAULT_CASE_INSENSITIVE;

        return builder;
    }

    /**
     * Instantiates a thread safe {@link PathExtractor} configured by this builder.
     * Attempts to build a "strict" PathExtractor which is much more performant, particularly for extractions with many
     * field names. Falls back to the "legacy" implementation if the paths registered are incompatible with the "strict"
     * implementation.
     * <br>
     * Use buildStrict to ensure the more optimal implementation is used.
     * @return new {@link PathExtractor} instance.
     */
    public PathExtractor<T> build() {
        try {
            return buildStrict();
        } catch (UnsupportedPathExpression e) {
            return buildLegacy();
        }
    }

    /**
     * Instantiate a "stricter" and more optimized PathExtractor.
     * <br>
     * Supports search paths where there is only one "variant" of step type from each parent step, and only one callback
     * per state.
     * Annotations matching is only supported on the root or wildcards.
     * Case insensitivity is supported on field names, not annotations.
     * <br>
     * Examples of supported paths (and any combination of the below):
     * `A::()`
     * `(foo bar)`
     * `(foo qux)`
     * `(spam 0)`
     * `(spam 1)`
     * `(quid * quo)`
     * `(lorem A::B::* ipsum)`
     * <br>
     * Examples of unsupported paths:
     * `(a::foo)` annotations on field names not supported, yet.
     * `(a::1)` annotations on index ordinals not supported, yet.
     * `(foo bar) (foo 1) (foo *)` combination of field names, index ordinals or wildcards not supported.
     * `a::() ()` combination of annotated and non-annotated root (or other wildcard) matching.
     *
     * @return new {@link PathExtractor} instance.
     * @throws UnsupportedPathExpression if any search path or the paths combined, are not supported.
     */
    public PathExtractor<T> buildStrict() {
        return buildStrict(false);
    }

    /**
     * Instantiate a "strict" path extractor, which also enforces type expectations.
     * <br>
     * Paths that attempt to find named children are only valid on Structs or untyped null.
     * Paths that attempt to find indexed (or wildcard) children are only valid on containers or untyped null.
     * For backwards compatibility that includes Structs, though they are defined as unordered per the Ion Datamodel.
     * <br>
     * The type check is performed _after_ any callbacks registered for the current path and
     * _before_ any child matches are attempted.
     */
    public PathExtractor<T> buildStrict(final boolean strictTyping) {
        return FsmPathExtractor.create(searchPaths,
                strictTyping,
                new PathExtractorConfig(matchRelativePaths, matchCaseInsensitive, matchFieldsCaseInsensitive));
    }

    /**
     * Instantiate a "legacy" PathExtractor implementation.
     * The returned PathExtractor is inefficient when a large number of field names is searched,
     * but a wider variety of search paths are supported.
     */
    public PathExtractor<T> buildLegacy() {
        return new PathExtractorImpl<>(searchPaths,
                new PathExtractorConfig(matchRelativePaths, matchCaseInsensitive, matchFieldsCaseInsensitive));
    }
    
    /**
     * Sets matchRelativePaths config. When true the path extractor will accept readers at any depth, when false the
     * reader must be at depth zero.
     *
     * <BR>
     * defaults to false.
     *
     * @param matchRelativePaths new config value.
     * @return builder for chaining.
     */
    public PathExtractorBuilder<T> withMatchRelativePaths(final boolean matchRelativePaths) {
        this.matchRelativePaths = matchRelativePaths;

        return this;
    }

    /**
     * Sets matchCaseInsensitive config. When true the path extractor will match fields _and annotations_ ignoring case.
     * When false the path extractor will match respecting the path components case.
     * To set case insensitivity for _only field names_ use the `withMatchFieldNamesCaseInsensitive` builder.
     *
     * <BR>
     * defaults to false.
     *
     * @param matchCaseInsensitive new config value.
     * @return builder for chaining.
     */
    public PathExtractorBuilder<T> withMatchCaseInsensitive(final boolean matchCaseInsensitive) {
        this.matchCaseInsensitive = matchCaseInsensitive;
        this.matchFieldsCaseInsensitive = matchCaseInsensitive;

        return this;
    }

    /**
     * Sets matchFieldNamesCaseInsensitive config. When true the path extractor will match field names ignoring case.
     * For example: 'Foo' will match 'foo'.
     *
     * <BR>
     * defaults to false.
     *
     * @param matchCaseInsensitive new config value.
     * @return builder for chaining.
     */
    public PathExtractorBuilder<T> withMatchFieldNamesCaseInsensitive(final boolean matchCaseInsensitive) {
        this.matchFieldsCaseInsensitive = matchCaseInsensitive;

        return this;
    }

    /**
     * Register a callback for a search path.
     *
     * @param searchPathAsIon string representation of a search path.
     * @param callback callback to be registered.
     * @return builder for chaining.
     * @see PathExtractorBuilder#withSearchPath(List, BiFunction, String[])
     */
    public PathExtractorBuilder<T> withSearchPath(final String searchPathAsIon,
                                                  final Function<IonReader, Integer> callback) {
        checkArgument(callback != null, "callback cannot be null");

        withSearchPath(searchPathAsIon, (reader, t) -> callback.apply(reader));

        return this;
    }

    /**
     * Register a callback for a search path.
     *
     * @param searchPathAsIon string representation of a search path.
     * @param callback callback to be registered.
     * @return builder for chaining.
     * @see PathExtractorBuilder#withSearchPath(List, BiFunction, String[])
     */
    public PathExtractorBuilder<T> withSearchPath(final String searchPathAsIon,
                                                  final BiFunction<IonReader, T, Integer> callback) {
        checkArgument(searchPathAsIon != null, "searchPathAsIon cannot be null");
        checkArgument(callback != null, "callback cannot be null");

        SearchPath<T> searchPath = SearchPathParser.parse(searchPathAsIon, callback);
        searchPaths.add(searchPath);

        return this;
    }

    /**
     * Register a callback for a search path.
     *
     * @param pathComponents search path as a list of path components.
     * @param callback callback to be registered.
     * @param annotations annotations used with this search path.
     * @return builder for chaining.
     */
    public PathExtractorBuilder<T> withSearchPath(final List<PathComponent> pathComponents,
                                                  final Function<IonReader, Integer> callback,
                                                  final String[] annotations) {
        checkArgument(callback != null, "callback cannot be null");

        return withSearchPath(pathComponents, (reader, t) -> callback.apply(reader), annotations);
    }

    /**
     * Register a callback for a search path.
     * <p>
     * The callback receives the matcher's {@link IonReader}, positioned on the matching value, so that it can use the
     * appropriate reader method to access the value. The callback return value is a ‘step-out-N’ instruction. The most
     * common value is zero, which tells the extractor to continue with the next value at the same depth. A return value
     * greater than zero may be useful to users who only care about the first match at a particular depth.
     * </p>
     *
     * <p>
     * Callback implementations <strong>MUST</strong> comply with the following:
     * </p>
     *
     * <ul>
     * <li>
     * The reader must not be advanced past the matching value. Violating this will cause the following value to be
     * skipped. If a value is skipped, neither the value itself nor any of its children will be checked for match
     * against any of the extractor's registered paths.
     * </li>
     * <li>
     * If the reader is positioned on a container value, its cursor must be at the same depth when the callback returns.
     * In other words, if the user steps in to the matched value, it must step out an equal number of times. Violating
     * this will raise an error.
     * </li>
     * <li>
     * Return value must be between zero and the the current reader relative depth, for example the following search
     * path (foo bar) must return values between 0 and 2 inclusive.
     * </li>
     * <li>
     * When there are nested search paths, e.g. (foo) and (foo bar), the callback for (foo) should not read the reader
     * value if it's a container. Doing so will advance the reader to the end of the container making impossible to
     * match (foo bar).
     * </li>
     * </ul>
     *
     * @param pathComponents search path as a list of path components.
     * @param callback callback to be registered.
     * @param annotations annotations used with this search path.
     * @return builder for chaining.
     */
    public PathExtractorBuilder<T> withSearchPath(final List<PathComponent> pathComponents,
                                                  final BiFunction<IonReader, T, Integer> callback,
                                                  final String[] annotations) {
        checkArgument(pathComponents != null, "pathComponents cannot be null");
        checkArgument(callback != null, "callback cannot be null");
        checkArgument(annotations != null, "annotations cannot be null");

        searchPaths.add(new SearchPath<>(pathComponents, callback, new Annotations(annotations)));

        return this;
    }

    /**
     * Add a search path by its components, with no annotations matching on the top-level-values.
     * <br>
     * @see PathExtractorBuilder#withSearchPath(List, BiFunction, String[])
     */
    public PathExtractorBuilder<T> withSearchPath(final List<PathComponent> pathComponents,
                                                  final BiFunction<IonReader, T, Integer> callback) {
        return withSearchPath(pathComponents, callback, EMPTY_STRING_ARRAY);
    }
}
