package com.fasterxml.jackson.dataformat.protobuf;

import java.io.*;

/**
 * Helper object used for buffering content for cases where we need (byte-)length prefixes
 * for content like packed arrays, embedded messages, Strings and (perhaps) binary content.
 */
public class ByteAccumulator
{
    protected final ByteAccumulator _parent;

    /**
     * Caller-provided buffer in which optional type prefix,
     * and mandatory length indicator may be added.
     * Caller ensures there is enough room for both, i.e. up
     * to 10 bytes (if both) or 5 bytes (if just length)
     */
    protected final byte[] _prefixBuffer;

    /**
     * Offset within {@link #_prefixBuffer} where there is room
     * for prefix.
     */
    protected final int _prefixOffset;
    
    protected final int _typedTag;

    protected Segment _firstSegment, _lastSegment;

    protected int _segmentBytes;

    public ByteAccumulator(ByteAccumulator p, int typedTag,
            byte[] prefixBuffer, int prefixOffset)
    {
        _parent = p;
        _typedTag = typedTag;
        _prefixBuffer = prefixBuffer;
        _prefixOffset = prefixOffset;
    }

    public ByteAccumulator(ByteAccumulator p,
            byte[] prefixBuffer, int prefixOffset) {
        _parent = p;
        _typedTag = -1;
        _prefixBuffer = prefixBuffer;
        _prefixOffset = prefixOffset;
    }

    public void append(byte[] buf, int offset, int len) {
        Segment s = new Segment(buf, offset, len);
        if (_lastSegment == null) {
            _firstSegment = _lastSegment = s;
        } else {
            _lastSegment = _lastSegment.linkNext(s);
        }
        _segmentBytes += len;
    }

    public ByteAccumulator finish(OutputStream out,
            byte[] input, int offset, int len) throws IOException
    {
        int start = _prefixOffset;
        int ptr;
        final byte[] prefix = _prefixBuffer;

        if (_typedTag == -1) {
            ptr = start;
        } else {
            ptr = ProtobufUtil.appendLengthLength(_typedTag, prefix, start);
        }

        int plen = _segmentBytes + len;

        ptr = ProtobufUtil.appendLengthLength(plen, prefix, ptr);

        // root? Just output it all 
        if (_parent == null) {
            out.write(prefix, start, ptr-start);
            for (Segment s = _firstSegment; s != null; s = s.next()) {
                s.writeTo(out);
            }
            if (len > 0) {
                out.write(input, offset, len);
            }
        } else {
            _parent.append(prefix, start, ptr-start);
            if (_firstSegment != null) {
                _parent.appendAll(_firstSegment, _lastSegment, _segmentBytes);
            }
            if (len > 0) {
                _parent.append(input, offset, len);
            }
        }
        return _parent;
    }

    public ByteAccumulator finish(OutputStream out) throws IOException
    {
        int start = _prefixOffset;
        int ptr;
        final byte[] prefix = _prefixBuffer;

        if (_typedTag == -1) {
            ptr = start;
        } else {
            ptr = ProtobufUtil.appendLengthLength(_typedTag, prefix, start);
        }
        int plen = _segmentBytes;
        ptr = ProtobufUtil.appendLengthLength(plen, prefix, ptr);

        // root? Just output it all 
        if (_parent == null) {
            out.write(prefix, start, ptr-start);
            for (Segment s = _firstSegment; s != null; s = s.next()) {
                s.writeTo(out);
            }
        } else {
            _parent.append(prefix, start, ptr-start);
            if (_firstSegment != null) {
                _parent.appendAll(_firstSegment, _lastSegment, _segmentBytes);
            }
        }
        return _parent;
    }

    private void appendAll(Segment first, Segment last, int segmentBytes)
    {
        _segmentBytes += segmentBytes;

        if (_firstSegment == null) {
            _firstSegment = first;
            _lastSegment = last;
        } else {
            _lastSegment.linkNext(first);
            _lastSegment = last;
        }
    }

    /*
    /**********************************************************
    /* Helper classes
    /**********************************************************
     */

    private final static class Segment
    {
        private final byte[] _buffer;
        private final int _start, _length;

        private Segment _next;

        public Segment(byte[] buffer, int start, int length) {
            _buffer = buffer;
            _start = start;
            _length = length;
        }

        public Segment linkNext(Segment next) {
            _next = next;
            return next;
        }

        public Segment next() {
            return _next;
        }
        public void writeTo(OutputStream out) throws IOException {
            out.write(_buffer, _start, _length);
        }
    }
}
