package com.fasterxml.jackson.dataformat.avro.testsupport;

import java.io.*;
import java.util.Random;

/**
 * Input stream that returns between [1, 6] bytes for each call, somewhat
 * randomly.
 */
public class LimitingInputStream
    extends FilterInputStream
{
    protected final InputStream _in;

    protected final Random _rnd;
    
    public LimitingInputStream(InputStream in, int seed) {
        super(in);
        _in = in;
        _rnd = new Random(seed);
    }

    public static LimitingInputStream wrap(InputStream in, int seed) {
        if (in instanceof LimitingInputStream) {
            return (LimitingInputStream) in;
        }
        return new LimitingInputStream(in, seed);
    }

    public static LimitingInputStream wrap(byte[] input, int seed) {
        return new LimitingInputStream(new ByteArrayInputStream(input), seed);
    }

    @Override
    public int read() throws IOException {
        return _in.read();
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }
    
    @Override
    public int read(byte[] buffer, int offset, int len0) throws IOException {
        int len = Math.min(len0, 1 + _rnd.nextInt(6));
        return _in.read(buffer, offset, len);
    }
    
}
