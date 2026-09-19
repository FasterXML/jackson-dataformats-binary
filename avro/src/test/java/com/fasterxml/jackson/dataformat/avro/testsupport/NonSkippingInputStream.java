package com.fasterxml.jackson.dataformat.avro.testsupport;

import java.io.*;

/**
 * Input stream that never skips anything: {@link #skip} returns 0 even when not
 * at end-of-input (something {@link InputStream#skip} is explicitly allowed to do,
 * and some real-world streams do). Content must hence be consumed via {@code read()}.
 *<p>
 * To keep a caller that does not handle this from spinning forever, an
 * {@link IOException} is thrown if {@link #skip} is called many times in a row
 * without any intervening read.
 *
 * @since 2.18.11
 */
public class NonSkippingInputStream
    extends FilterInputStream
{
    protected final static int MAX_CONSECUTIVE_SKIPS = 1000;

    protected int _consecutiveSkips;

    public NonSkippingInputStream(InputStream in) {
        super(in);
    }

    public static NonSkippingInputStream wrap(byte[] input) {
        return new NonSkippingInputStream(new ByteArrayInputStream(input));
    }

    @Override
    public long skip(long n) throws IOException {
        if (++_consecutiveSkips > MAX_CONSECUTIVE_SKIPS) {
            throw new IOException("skip() called "+_consecutiveSkips
                    +" times without intervening read(): caller not making progress");
        }
        return 0L;
    }

    @Override
    public int read() throws IOException {
        _consecutiveSkips = 0;
        return in.read();
    }

    @Override
    public int read(byte[] b, int offset, int len) throws IOException {
        _consecutiveSkips = 0;
        return in.read(b, offset, len);
    }
}
