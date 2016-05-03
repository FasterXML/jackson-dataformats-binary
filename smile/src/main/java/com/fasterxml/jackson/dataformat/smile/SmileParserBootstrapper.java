package com.fasterxml.jackson.dataformat.smile;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.format.InputAccessor;
import com.fasterxml.jackson.core.format.MatchStrength;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;

import static com.fasterxml.jackson.dataformat.smile.SmileConstants.*;

/**
 * Simple bootstrapper version used with Smile format parser.
 */
public class SmileParserBootstrapper
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

    protected int _inputPtr;

    protected int _inputEnd;

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

    public SmileParserBootstrapper(IOContext ctxt, InputStream in)
    {
        _context = ctxt;
        _in = in;
        _inputBuffer = ctxt.allocReadIOBuffer();
        _inputEnd = _inputPtr = 0;
        _inputProcessed = 0;
        _bufferRecyclable = true;
    }

    public SmileParserBootstrapper(IOContext ctxt, byte[] inputBuffer, int inputStart, int inputLen)
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

    public SmileParser constructParser(int factoryFeatures,
            int generalParserFeatures, int smileFeatures,
            ObjectCodec codec, ByteQuadsCanonicalizer rootByteSymbols)
        throws IOException, JsonParseException
    {
        ByteQuadsCanonicalizer can = rootByteSymbols.makeChild(factoryFeatures);
        // We just need a single byte, really, to know if it starts with header
        int end = _inputEnd;
        if (_inputPtr < end && _in != null) {
        	int count = _in.read(_inputBuffer, end, _inputBuffer.length - end);
        	if (count > 0) {
                _inputEnd += count;
            }
        }

        SmileParser p = new SmileParser(_context, generalParserFeatures, smileFeatures,
                codec, can, 
                _in, _inputBuffer, _inputPtr, _inputEnd, _bufferRecyclable);
        boolean hadSig = false;
        if (_inputPtr < _inputEnd) { // only false for empty doc
            if (_inputBuffer[_inputPtr] == SmileConstants.HEADER_BYTE_1) {
                // need to ensure it gets properly handled so caller won't see the signature
                hadSig = p.handleSignature(true, true);
            }
        } else {
            /* 11-Oct-2012, tatu: Actually, let's allow empty documents even if
             *   header signature would otherwise be needed. This is useful for
             *   JAX-RS provider, empty PUT/POST payloads.
             */
            return p;
        }
        if (!hadSig && SmileParser.Feature.REQUIRE_HEADER.enabledIn(smileFeatures)) {
            // Ok, first, let's see if it looks like plain JSON...
            String msg;

            byte firstByte = (_inputPtr < _inputEnd) ? _inputBuffer[_inputPtr] : 0;
            if (firstByte == '{' || firstByte == '[') {
                msg = "Input does not start with Smile format header (first byte = 0x"
                    +Integer.toHexString(firstByte & 0xFF)+") -- rather, it starts with '"+((char) firstByte)
                    +"' (plain JSON input?) -- can not parse";
            } else {
                msg = "Input does not start with Smile format header (first byte = 0x"
                +Integer.toHexString(firstByte & 0xFF)+") and parser has REQUIRE_HEADER enabled: can not parse";
            }
            throw new JsonParseException(p, msg);
        }
        return p;
    }

    /*
    /**********************************************************
    /*  Encoding detection for data format auto-detection
    /**********************************************************
     */

    public static MatchStrength hasSmileFormat(InputAccessor acc) throws IOException
    {
        // Ok: ideally we start with the header -- if so, we are golden
        if (!acc.hasMoreBytes()) {
            return MatchStrength.INCONCLUSIVE;
        }
        // We always need at least two bytes to determine, so
        byte b1 = acc.nextByte();
        if (!acc.hasMoreBytes()) {
            return MatchStrength.INCONCLUSIVE;
        }
        byte b2 = acc.nextByte();
        
        // First: do we see 3 "magic bytes"? If so, we are golden
        if (b1 == SmileConstants.HEADER_BYTE_1) { // yeah, looks like marker
            if (b2 != SmileConstants.HEADER_BYTE_2) {
                return MatchStrength.NO_MATCH;
            }
            if (!acc.hasMoreBytes()) {
                return MatchStrength.INCONCLUSIVE;
            }
            return (acc.nextByte() == SmileConstants.HEADER_BYTE_3) ?
                    MatchStrength.FULL_MATCH : MatchStrength.NO_MATCH;
        }
        // Otherwise: ideally either Object or Array:
        if (b1 == SmileConstants.TOKEN_LITERAL_START_OBJECT) {
            /* Object is bit easier, because now we need to get new name; i.e. can
             * rule out name back-refs
             */
            if (b2 == SmileConstants.TOKEN_KEY_LONG_STRING) {
                return MatchStrength.SOLID_MATCH;
            }
            int ch = (int) b2 & 0xFF;
            if (ch >= 0x80 && ch < 0xF8) {
                return MatchStrength.SOLID_MATCH;
            }
            return MatchStrength.NO_MATCH;
        }
        // Array bit trickier
        if (b1 == SmileConstants.TOKEN_LITERAL_START_ARRAY) {
            if (!acc.hasMoreBytes()) {
                return MatchStrength.INCONCLUSIVE;
            }
            /* For arrays, we will actually accept much wider range of values (including
             * things that could otherwise collide)
             */
            if (likelySmileValue(b2) || possibleSmileValue(b2, true)) {
                return MatchStrength.SOLID_MATCH;
            }
            return MatchStrength.NO_MATCH;
        }
        // Scalar values are pretty weak, albeit possible; require more certain match, consider it weak:
        if (likelySmileValue(b1) || possibleSmileValue(b2, false)) {
            return MatchStrength.SOLID_MATCH;
        }
        return MatchStrength.NO_MATCH;
    }

    private static boolean likelySmileValue(byte b)
    {
        if (   (b == TOKEN_MISC_LONG_TEXT_ASCII) // 0xE0
            || (b == TOKEN_MISC_LONG_TEXT_UNICODE) // 0xE4
            || (b == TOKEN_MISC_BINARY_7BIT) // 0xE8
            || (b == TOKEN_LITERAL_START_ARRAY) // 0xF8
            || (b == TOKEN_LITERAL_START_OBJECT) // 0xFA
            ) {
            return true;
        }
        int ch = b & 0xFF;
        // ASCII ctrl char range is pretty good match too
        if (ch >= 0x80 && ch <= 0x9F) {
            return true;
        }
        return false;
    }

    /**
     * @param lenient Whether to consider more speculative matches or not
     *   (typically true when there is context like start-array)
     */
    private static boolean possibleSmileValue(byte b, boolean lenient)
    {
        int ch = (int) b & 0xFF;
        // note: we know that likely matches have been handled already, so...
        if (ch >= 0x80) {
            return (ch <= 0xE0);
        }
        if (lenient) {
            if (ch >= 0x40) { // tiny/short ASCII
                return true;
            }
            if (ch >- 0x20) { // various constants
                return (ch < 0x2C); // many reserved bytes that can't be seen
            }
        }
        return false;
    }
}
