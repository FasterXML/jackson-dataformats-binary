package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.format.InputAccessor;
import com.fasterxml.jackson.core.format.MatchStrength;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;

/**
 * Simple bootstrapper version used with CBOR format parser.
 */
public class CBORParserBootstrapper
{
    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    protected final IOContext _context;
    protected final InputStream _in;
    
    /*
    /**********************************************************
    /* Input buffering
    /**********************************************************
     */

    protected final byte[] _inputBuffer;
    protected int _inputPtr, _inputEnd;

    /**
     * Flag that indicates whether buffer above is to be recycled
     * after being used or not.
     */
    protected final boolean _bufferRecyclable;

    /*
    /**********************************************************
    /* Input location
    /**********************************************************
     */

    /**
     * Current number of input units (bytes or chars) that were processed in
     * previous blocks,
     * before contents of current input buffer.
     *<p>
     * Note: includes possible BOMs, if those were part of the input.
     */
    protected int _inputProcessed;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public CBORParserBootstrapper(IOContext ctxt, InputStream in)
    {
        _context = ctxt;
        _in = in;
        _inputBuffer = ctxt.allocReadIOBuffer();
        _inputEnd = _inputPtr = 0;
        _inputProcessed = 0;
        _bufferRecyclable = true;
    }

    public CBORParserBootstrapper(IOContext ctxt, byte[] inputBuffer, int inputStart, int inputLen)
    {
        _context = ctxt;
        _in = null;
        _inputBuffer = inputBuffer;
        _inputPtr = inputStart;
        _inputEnd = (inputStart + inputLen);
        // Need to offset this for correct location info
        _inputProcessed = -inputStart;
        _bufferRecyclable = false;
    }

    public CBORParser constructParser(int factoryFeatures,
            int generalParserFeatures, int formatFeatures,
            ObjectCodec codec, ByteQuadsCanonicalizer rootByteSymbols)
        throws IOException, JsonParseException
    {
        ByteQuadsCanonicalizer can = rootByteSymbols.makeChild(factoryFeatures);
        // We just need a single byte to recognize possible "empty" document.
        ensureLoaded(1);
        CBORParser p = new CBORParser(_context, generalParserFeatures, formatFeatures,
                codec, can, 
                _in, _inputBuffer, _inputPtr, _inputEnd, _bufferRecyclable);
        if (_inputPtr < _inputEnd) { // only false for empty doc
            ; // anything we should verify? In future, could verify
        } else {
            /* 13-Jan-2014, tatu: Actually, let's allow empty documents even if
             *   header signature would otherwise be needed. This is useful for
             *   JAX-RS provider, empty PUT/POST payloads?
             */
            ;
        }
        return p;
    }

    /*
    /**********************************************************
    /*  Encoding detection for data format auto-detection
    /**********************************************************
     */

    public static MatchStrength hasCBORFormat(InputAccessor acc) throws IOException
    {
        // Ok: ideally we start with the header -- if so, we are golden
        if (!acc.hasMoreBytes()) {
            return MatchStrength.INCONCLUSIVE;
        }
        // We always need at least two bytes to determine, so
        byte b = acc.nextByte();

        /* 13-Jan-2014, tatu: Let's actually consider indefine-length Objects
         *    as conclusive matches if empty, or start with a text key.
         */
        if (b == CBORConstants.BYTE_OBJECT_INDEFINITE) {
            if (acc.hasMoreBytes()) {
                b = acc.nextByte();
                if (b == CBORConstants.BYTE_BREAK) {
                    return MatchStrength.SOLID_MATCH;
                }
                if (CBORConstants.hasMajorType(CBORConstants.MAJOR_TYPE_TEXT, b)) {
                    return MatchStrength.SOLID_MATCH;
                }
                // other types; unlikely but can't exactly rule out
                return MatchStrength.INCONCLUSIVE;
            }
        } else if (b == CBORConstants.BYTE_ARRAY_INDEFINITE) {
            if (acc.hasMoreBytes()) {
                b = acc.nextByte();
                if (b == CBORConstants.BYTE_BREAK) {
                    return MatchStrength.SOLID_MATCH;
                }
                // all kinds of types are possible, so let's just acknowledge it as possible:
                return MatchStrength.WEAK_MATCH;
            }
        } else if (CBORConstants.hasMajorType(CBORConstants.MAJOR_TYPE_TAG, b)) {

            // Actually, specific "self-describe tag" is a very good indicator
            // (see [Issue#6]
            if (b == (byte) 0xD9) {
                if (acc.hasMoreBytes()) {
                    b = acc.nextByte();
                    if (b == (byte) 0xD9) {
                        if (acc.hasMoreBytes()) {
                            b = acc.nextByte();
                            if (b == (byte) 0xF7) {
                                return MatchStrength.FULL_MATCH;
                            }
                        }
                    }
                }
            }
            // As to other tags, possible. May want to add other "well-known" (commonly
            // used ones for root value) tags for 'solid' match in future.
            return MatchStrength.WEAK_MATCH;

        // Other types; the only one where there's significant checking possibility
        // is in last, "misc" category
        } else if (CBORConstants.hasMajorType(CBORConstants.MAJOR_TYPE_MISC, b)) {
            if ((b == CBORConstants.BYTE_FALSE)
                    || (b == CBORConstants.BYTE_TRUE)
                    || (b == CBORConstants.BYTE_NULL)) {
                return MatchStrength.SOLID_MATCH;
            }
            return MatchStrength.NO_MATCH;
        }
        return MatchStrength.INCONCLUSIVE;
    }
    
    /*
    /**********************************************************
    /* Internal methods, raw input access
    /**********************************************************
     */

    protected boolean ensureLoaded(int minimum) throws IOException
    {
        if (_in == null) { // block source; nothing more to load
            return false;
        }

        /* Let's assume here buffer has enough room -- this will always
         * be true for the limited used this method gets
         */
        int gotten = (_inputEnd - _inputPtr);
        while (gotten < minimum) {
            int count = _in.read(_inputBuffer, _inputEnd, _inputBuffer.length - _inputEnd);
            if (count < 1) {
                return false;
            }
            _inputEnd += count;
            gotten += count;
        }
        return true;
    }
}
