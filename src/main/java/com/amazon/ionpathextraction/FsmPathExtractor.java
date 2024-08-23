package com.amazon.ionpathextraction;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ionpathextraction.internal.PathExtractorConfig;

import java.util.List;
import java.util.function.BiFunction;

import static com.amazon.ionpathextraction.internal.Preconditions.checkArgument;
import static com.amazon.ionpathextraction.internal.Preconditions.checkState;

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

    private FsmPathExtractor(FsmMatcher<T> rootMatcher, PathExtractorConfig config) {
        this.rootMatcher = rootMatcher;
        this.config = config;
    }

    static <U> FsmPathExtractor<U> create(List<SearchPath<U>> searchPaths, PathExtractorConfig config) {
        FsmMatcherBuilder<U> builder = new FsmMatcherBuilder<>(config.isMatchCaseInsensitive(), config.isMatchFieldsCaseInsensitive());
        for (SearchPath<U> path : searchPaths) {
            builder.accept(path);
        }

        return new FsmPathExtractor<>(builder.build(), config);
    }

    @Override
    public void match(IonReader reader) {
        match(reader, null);
    }

    @Override
    public void match(IonReader reader, T context) {
        checkArgument(reader.getDepth() == 0 || config.isMatchRelativePaths(),
                "reader must be at depth zero, it was at: " + reader.getDepth());

        while (reader.next() != null) {
            matchCurrentValue(reader, context);
        }
    }

    @Override
    public void matchCurrentValue(IonReader reader) {
        matchCurrentValue(reader, null);
    }

    @Override
    public void matchCurrentValue(IonReader reader, T context) {
        checkArgument(reader.getDepth() == 0 || config.isMatchRelativePaths(),
                "reader must be at depth zero, it was at: " + reader.getDepth());
        checkArgument(reader.getType() != null,
                "reader must be positioned at a value; call IonReader.next() first.");

        match(reader, rootMatcher, context, null, reader.getDepth());
    }

    private int match(
            IonReader reader,
            FsmMatcher<T> matcher,
            T context,
            Integer position,
            int initialDepth) {
        FsmMatcher<T> child = matcher.transition(reader.getFieldName(), position, reader.getTypeAnnotations());
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
            while (reader.next() != null && stepOut == 0) {
                stepOut = match(reader, child, context, childPos++, initialDepth);
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
}
