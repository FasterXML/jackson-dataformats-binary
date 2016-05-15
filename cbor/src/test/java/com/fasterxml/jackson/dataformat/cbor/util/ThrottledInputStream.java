package com.fasterxml.jackson.dataformat.cbor.util;

import java.io.*;

public class ThrottledInputStream extends FilterInputStream
{
    protected final int _maxBytes;

    public ThrottledInputStream(byte[] data, int maxBytes)
    {
        this(new ByteArrayInputStream(data), maxBytes);
    }
    
    public ThrottledInputStream(InputStream in, int maxBytes)
    {
        super(in);
        _maxBytes = maxBytes;
    }

    @Override
    public int read(byte[] buf) throws IOException {
        return read(buf, 0, buf.length);
    }
    
    @Override
    public int read(byte[] buf, int offset, int len) throws IOException {
        return in.read(buf, offset, Math.min(_maxBytes, len));
    }
}
