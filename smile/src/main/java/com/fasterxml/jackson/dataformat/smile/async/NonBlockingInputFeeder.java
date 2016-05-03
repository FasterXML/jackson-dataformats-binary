package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;

/**
 * Interface used by non-blocking {@link com.fasterxml.jackson.core.JsonParser}
 * to get more input to parse.
 * It is accessed by entity that feeds content to parse; at any given point
 * only one chunk of content can be processed so caller has to take care to
 * only feed more content when existing content has been parsed (which occurs
 * when parser's <code>nextToken</code> is called). Once application using
 * non-blocking parser has no more data to feed it should call
 * {@link #endOfInput} to indicate end of logical input stream.
 * 
 * @author Tatu Saloranta
 */
public interface NonBlockingInputFeeder
{
    /**
     * Method called to check whether it is ok to feed more data: parser returns true
     * if it has no more content to parse (and it is ok to feed more); otherwise false
     * (and no data should yet be fed).
     */
    public boolean needMoreInput();

    /**
     * Method that can be called to feed more data, if (and only if)
     * {@link #needMoreInput} returns true.
     * 
     * @param data Byte array that contains data to feed: caller must ensure data remains
     *    stable until it is fully processed (which is true when {@link #needMoreInput}
     *    returns true)
     * @param offset Offset within array where input data to process starts
     * @param len Length of input data within array to process.
     * 
     * @throws IOException if the state is such that this method should not be called
     *   (has not yet consumed existing input data, or has been marked as closed)
     */
    public void feedInput(byte[] data, int offset, int len) throws IOException;

    /**
     * Method that should be called after last chunk of data to parse has been fed
     * (with {@link #feedInput}); can be called regardless of what {@link #needMoreInput}
     * returns. After calling this method, no more data can be fed; and parser assumes
     * no more data will be available.
     */
    public void endOfInput();
}
