package com.fasterxml.jackson.dataformat.avro.testsupport;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

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

    public static ThrottledInputStream wrap(InputStream in, int maxBytes) {
        if (in instanceof ThrottledInputStream) {
            return (ThrottledInputStream) in;
        }
        return new ThrottledInputStream(in, maxBytes);
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