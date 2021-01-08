package com.fasterxml.jackson.dataformat.smile;

import java.io.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;

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

    protected final IOContext _ioContext;

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
        _ioContext = ctxt;
        _in = in;
        _inputBuffer = ctxt.allocReadIOBuffer();
        _inputEnd = _inputPtr = 0;
        _inputProcessed = 0;
        _bufferRecyclable = true;
    }

    public SmileParserBootstrapper(IOContext ctxt, byte[] inputBuffer, int inputStart, int inputLen)
    {
        _ioContext = ctxt;
        _in = null;
        _inputBuffer = inputBuffer;
        _inputPtr = inputStart;
        _inputEnd = (inputStart + inputLen);
        // Need to offset this for correct location info
        _inputProcessed = -inputStart;
        _bufferRecyclable = false;
    }

    public SmileParser constructParser(ObjectReadContext readCtxt,
            int factoryFeatures,
            int generalParserFeatures, int smileFeatures,
            ByteQuadsCanonicalizer rootByteSymbols)
        throws IOException, JsonParseException
    {
        ByteQuadsCanonicalizer can = rootByteSymbols.makeChild(factoryFeatures);
        // We just need a single byte, really, to know if it starts with header
        int end = _inputEnd;
        if ((_inputPtr < end) && (_in != null)) {
            int count = _in.read(_inputBuffer, end, _inputBuffer.length - end);
            if (count > 0) {
                _inputEnd += count;
            }
        }

        SmileParser p = new SmileParser(readCtxt, _ioContext, generalParserFeatures, smileFeatures,
                can, 
                _in, _inputBuffer, _inputPtr, _inputEnd, _bufferRecyclable);
        boolean hadSig = false;

        if (_inputPtr >= _inputEnd) { // only the case for empty doc
            // 11-Oct-2012, tatu: Actually, let's allow empty documents even if
            //   header signature would otherwise be needed. This is useful for
            //   JAX-RS provider, empty PUT/POST payloads.
            return p;
        }
        final byte firstByte = _inputBuffer[_inputPtr];
        if (firstByte == SmileConstants.HEADER_BYTE_1) {
            // need to ensure it gets properly handled so caller won't see the signature
            hadSig = p.handleSignature(true, true);
        }

        if (!hadSig && SmileParser.Feature.REQUIRE_HEADER.enabledIn(smileFeatures)) {
            // Ok, first, let's see if it looks like plain JSON...
            String msg;

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
}
