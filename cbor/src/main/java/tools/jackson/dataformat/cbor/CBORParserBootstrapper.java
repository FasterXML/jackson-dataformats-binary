package tools.jackson.dataformat.cbor;

import java.io.*;

import tools.jackson.core.*;
import tools.jackson.core.exc.WrappedIOException;
import tools.jackson.core.io.IOContext;
import tools.jackson.core.sym.ByteQuadsCanonicalizer;

/**
 * Simple bootstrapper version used with CBOR format parser.
 */
public class CBORParserBootstrapper
{
    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

    protected final IOContext _ioContext;
    protected final InputStream _in;
    
    /*
    /**********************************************************************
    /* Input buffering
    /**********************************************************************
     */

    protected final byte[] _inputBuffer;
    protected int _inputPtr, _inputEnd;

    /**
     * Flag that indicates whether buffer above is to be recycled
     * after being used or not.
     */
    protected final boolean _bufferRecyclable;

    /*
    /**********************************************************************
    /* Input location
    /**********************************************************************
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
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    public CBORParserBootstrapper(IOContext ioCtxt, InputStream in)
    {
        _ioContext = ioCtxt;
        _in = in;
        _inputBuffer = ioCtxt.allocReadIOBuffer();
        _inputEnd = _inputPtr = 0;
        _inputProcessed = 0;
        _bufferRecyclable = true;
    }

    public CBORParserBootstrapper(IOContext ioCtxt, byte[] inputBuffer, int inputStart, int inputLen)
    {
        _ioContext = ioCtxt;
        _in = null;
        _inputBuffer = inputBuffer;
        _inputPtr = inputStart;
        _inputEnd = (inputStart + inputLen);
        // Need to offset this for correct location info
        _inputProcessed = -inputStart;
        _bufferRecyclable = false;
    }

    public CBORParser constructParser(ObjectReadContext readCtxt,
            int factoryFeatures,
            int generalParserFeatures, int formatFeatures,
            ByteQuadsCanonicalizer rootByteSymbols)
        throws JacksonException
    {
        // 13-Mar-2021, tatu: [dataformats-binary#253] Create canonicalizing OR
        //    placeholder, depending on settings
        ByteQuadsCanonicalizer can = rootByteSymbols.makeChildOrPlaceholder(factoryFeatures);
        // We just need a single byte to recognize possible "empty" document.
        ensureLoaded(1);
        CBORParser p = new CBORParser(readCtxt, _ioContext,
                generalParserFeatures, formatFeatures,
                can, 
                _in, _inputBuffer, _inputPtr, _inputEnd, _bufferRecyclable);
        if (_inputPtr < _inputEnd) { // only false for empty doc
            ; // anything we should verify? In future, could verify
        } else {
            // 13-Jan-2014, tatu: Actually, let's allow empty documents even if
            //   header signature would otherwise be needed. This is useful for
            //   JAX-RS provider, empty PUT/POST payloads?
            ;
        }
        return p;
    }

    /*
    /**********************************************************************
    /* Internal methods, raw input access
    /**********************************************************************
     */

    protected boolean ensureLoaded(int minimum) throws JacksonException
    {
        if (_in == null) { // block source; nothing more to load
            return false;
        }

        /* Let's assume here buffer has enough room -- this will always
         * be true for the limited used this method gets
         */
        int gotten = (_inputEnd - _inputPtr);
        while (gotten < minimum) {
            int count;
            try {
                count = _in.read(_inputBuffer, _inputEnd, _inputBuffer.length - _inputEnd);
            } catch (IOException e) {
                throw WrappedIOException.construct(e);
            }
            if (count < 1) {
                return false;
            }
            _inputEnd += count;
            gotten += count;
        }
        return true;
    }
}
