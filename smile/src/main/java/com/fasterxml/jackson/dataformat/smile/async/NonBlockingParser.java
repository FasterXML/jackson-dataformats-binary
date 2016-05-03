package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Mix-in interface used with {@link com.fasterxml.jackson.core.JsonParser},
 * extending it with features needed to process data in non-blocking
 * ("asynchronous")
 */
public interface NonBlockingParser
    extends NonBlockingInputFeeder
{
    /**
     * Method that can be called when current token is not yet
     * available via {@link com.fasterxml.jackson.core.JsonParser#getCurrentToken},
     * to try to figure out what kind of token will be eventually returned
     * once the whole token is decoded, if known.
     * Note that this may return {@link com.fasterxml.jackson.core.JsonToken#NOT_AVAILABLE}:
     * this occurs either if current token is known (and thus no more
     * parsing can be done yet), or if not enough content is available
     * to even determine next token type (typically we only need a single
     * byte, but in boundaries zero bytes is available).
     * 
     * @return Token that will eventually be returned with
     *    a call to {@link com.fasterxml.jackson.core.JsonParser#nextToken}, if known
     */
    public JsonToken peekNextToken() throws IOException, JsonParseException;
}
