package com.fasterxml.jackson.dataformat.smile.testutil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ByteOutputStreamForTesting extends ByteArrayOutputStream
{
    public int closeCount = 0;
    public int flushCount = 0;

    public ByteOutputStreamForTesting() { }

    @Override
    public void close() throws IOException {
        ++closeCount;
        super.close();
    }

    @Override
    public void flush() throws IOException
    {
        ++flushCount;
        super.flush();
    }

    public boolean isClosed() { return closeCount > 0; }
    public boolean isFlushed() { return flushCount > 0; }
}
