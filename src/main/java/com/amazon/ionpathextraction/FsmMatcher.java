package com.amazon.ionpathextraction;

import com.amazon.ion.IonReader;

import java.util.function.BiFunction;

/**
 * Base class for match states in the Finite-State-Machine matching implementation.
 */
abstract class FsmMatcher<T>
{
    /**
     * Callback for match state. May be null.
     */
    BiFunction<IonReader, T, Integer> callback;

    /**
     * Indicates there are no possible child transitions.
     */
    boolean terminal = false;

    /**
     * Return the child matcher for the given reader context.
     * Return null if there is no match.
     */
    abstract FsmMatcher<T> transition(String fieldName, Integer position, String[] annotations);
}
