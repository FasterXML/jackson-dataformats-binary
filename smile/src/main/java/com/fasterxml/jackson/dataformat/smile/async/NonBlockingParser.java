package com.fasterxml.jackson.dataformat.smile.async;

/**
 * Mix-in interface used with {@link com.fasterxml.jackson.core.JsonParser},
 * extending it with features needed to process data in non-blocking
 * ("asynchronous")
 */
public interface NonBlockingParser<F extends NonBlockingInputFeeder>
{
    /**
     * Accessor for getting handle to the input feeder to use for this parser
     */
    public F getInputFeeder();
}
