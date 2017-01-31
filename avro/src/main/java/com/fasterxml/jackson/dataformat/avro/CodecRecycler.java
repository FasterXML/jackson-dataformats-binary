package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;

import org.apache.avro.Schema;
import org.apache.avro.io.*;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Simple helper class that contains extracted functionality for
 * simple encoder/decoder recycling.
 *
 * @since 2.8.7
 */
public final class CodecRecycler
{
    protected final static DecoderFactory DECODER_FACTORY = DecoderFactory.get();

    protected final static EncoderFactory ENCODER_FACTORY = EncoderFactory.get();

    protected final static ThreadLocal<SoftReference<CodecRecycler>> _recycler
            = new ThreadLocal<SoftReference<CodecRecycler>>();

    private BinaryDecoder decoder;
    private BinaryEncoder encoder;

    private CodecRecycler() { }

    /*
    /**********************************************************
    /* Public API
    /**********************************************************
     */

    public static BinaryDecoder decoder(InputStream in, boolean buffering)
    {
        BinaryDecoder prev = _recycler().claimDecoder();
        return buffering
                ? DECODER_FACTORY.binaryDecoder(in, prev)
                : DECODER_FACTORY.directBinaryDecoder(in, prev);
    }

    public static BinaryDecoder decoder(byte[] buffer, int offset, int len)
    {
        BinaryDecoder prev = _recycler().claimDecoder();
        return DECODER_FACTORY.binaryDecoder(buffer, offset, len, prev);
    }

    public static ResolvingDecoder convertingDecoder(Decoder src,
            Schema writer, Schema reader)
        throws JsonProcessingException
    {
        try {
            return DECODER_FACTORY.resolvingDecoder(Schema.applyAliases(writer, reader), reader, src);
        } catch (IOException e) {
            throw new BadSchemaException("Failed to create reader/writer-resolving Avro schema handler: "+e.getMessage(), e);
        }
    }

    public static BinaryEncoder encoder(OutputStream out, boolean buffering)
    {
        BinaryEncoder prev = _recycler().claimEncoder();
        return buffering
            ? ENCODER_FACTORY.binaryEncoder(out, prev)
            : ENCODER_FACTORY.directBinaryEncoder(out, prev);
    }

    public static void release(BinaryDecoder dec) {
        _recycler().decoder = (BinaryDecoder) dec;
    }

    public static void release(BinaryEncoder enc) {
        _recycler().encoder = enc;
    }

    /*
    /**********************************************************
    /* Internal per-instance methods
    /**********************************************************
     */
    
    private static CodecRecycler _recycler() {
        SoftReference<CodecRecycler> ref = _recycler.get();
        CodecRecycler r = (ref == null) ? null : ref.get();

        if (r == null) {
            r = new CodecRecycler();
            _recycler.set(new SoftReference<CodecRecycler>(r));
        }
        return r;
    }

    private BinaryDecoder claimDecoder() {
        BinaryDecoder d = decoder;
        decoder = null;
        return d;
    }

    private BinaryEncoder claimEncoder() {
        BinaryEncoder e = encoder;
        encoder = null;
        return e;
    }

    /*
    /**********************************************************
    /* Helper class
    /**********************************************************
     */

    public static class BadSchemaException extends JsonProcessingException
    {
        private static final long serialVersionUID = 1L;

        public BadSchemaException(String msg, Throwable src) {
            super(msg, src);
        }
    }
}
