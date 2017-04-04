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
     * Offset within {@link #_prefixBuffer} where there is room for encoding
     * prefix (type, tag, length).
     */
    protected final int _prefixOffset;

    protected final int _typedTag;

    protected Segment _firstSegment, _lastSegment;

    /**
     * Total number of bytes contained within buffers, to be used for length prefix.
     */
    protected int _segmentBytes;

    /**
     * Pointer to start of contents provided by parent, preceding room
     * for prefix (that is, same as or less than `_prefixOffset`)
     * 
     * @since 2.8.8
     */
    protected int _parentStart;

    public ByteAccumulator(ByteAccumulator p, int typedTag,
            byte[] prefixBuffer, int prefixOffset, int parentStart)
    {
        _parent = p;
        _typedTag = typedTag;
        _prefixBuffer = prefixBuffer;
        _prefixOffset = prefixOffset;
        _parentStart = parentStart;
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
        final byte[] prefixBuf = _prefixBuffer;
        int ptr;

        // First: encode full tag to use, now that we length to calculate prefix from
        if (_typedTag == -1) {
            ptr = _prefixOffset;
        } else {
            ptr = ProtobufUtil.appendLengthLength(_typedTag, prefixBuf, _prefixOffset);
        }

        int plen = _segmentBytes + len;
        ptr = ProtobufUtil.appendLengthLength(plen, prefixBuf, ptr);

        // root? Just output it all 
        final int writeStart = _parentStart; // same as `_prefixOffset` or less, if buffered content
        if (_parent == null) {
            // 04-Apr-2017, tatu: We know that parent will have flushed anything it might have,
            //    so `_parentStart` is irrelevant here (but not in the other branch)
            out.write(prefixBuf, writeStart, ptr-writeStart);
            for (Segment s = _firstSegment; s != null; s = s.next()) {
                s.writeTo(out);
            }
            if (len > 0) {
                out.write(input, offset, len);
            }
        } else {
            // 04-Apr-2017, tatu: for [dataformats-binary#67], need to flush possible
            //    content parent had...
            _parent.append(prefixBuf, writeStart, ptr-writeStart);
            if (_firstSegment != null) {
                _parent.appendAll(_firstSegment, _lastSegment, _segmentBytes);
            }
            if (len > 0) {
                _parent.append(input, offset, len);
            }
        }
        return _parent;
    }

    public ByteAccumulator finish(OutputStream out, byte[] input) throws IOException
    {
        int ptr;
        final byte[] prefixBuf = _prefixBuffer;

        if (_typedTag == -1) {
            ptr = _prefixOffset;
        } else {
            ptr = ProtobufUtil.appendLengthLength(_typedTag, prefixBuf, _prefixOffset);
        }
        int plen = _segmentBytes;
        ptr = ProtobufUtil.appendLengthLength(plen, prefixBuf, ptr);

        final int writeStart = _parentStart; // same as `_prefixOffset` or less, if buffered content
        // root? Just output it all 
        if (_parent == null) {
            // 04-Apr-2017, tatu: We know that parent will have flushed anything it might have,
            //    so `_parentStart` is irrelevant here (but not in the other branch)
            out.write(prefixBuf, writeStart, ptr-writeStart);
            for (Segment s = _firstSegment; s != null; s = s.next()) {
                s.writeTo(out);
            }
        } else {
            _parent.append(prefixBuf, writeStart, ptr-writeStart);
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
