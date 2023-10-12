package com.fasterxml.jackson.dataformat.avro.apacheimpl;

import java.lang.ref.SoftReference;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.avro.io.*;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Simple helper class that contains extracted functionality for
 * simple encoder/decoder recycling.
 *
 * @since 2.8.7
 */
public final class ApacheCodecRecycler
{
    protected final static ThreadLocal<SoftReference<ApacheCodecRecycler>> _recycler
            = new ThreadLocal<SoftReference<ApacheCodecRecycler>>();

    private final AtomicReference<BinaryDecoder> decoderRef = new AtomicReference<>();
    private final AtomicReference<BinaryEncoder> encoderRef = new AtomicReference<>();

    private ApacheCodecRecycler() { }

    /*
    /**********************************************************
    /* Public API
    /**********************************************************
     */

    public static BinaryDecoder acquireDecoder() {
        return _recycler().claimDecoder();
    }

    public static BinaryEncoder acquireEncoder() {
        return _recycler().claimEncoder();
    }

    public static void release(BinaryDecoder dec) {
        _recycler().decoderRef.set(dec);
    }

    public static void release(BinaryEncoder enc) {
        _recycler().encoderRef.set(enc);
    }

    /*
    /**********************************************************
    /* Internal per-instance methods
    /**********************************************************
     */

    private static ApacheCodecRecycler _recycler() {
        SoftReference<ApacheCodecRecycler> ref = _recycler.get();
        ApacheCodecRecycler r = (ref == null) ? null : ref.get();

        if (r == null) {
            r = new ApacheCodecRecycler();
            _recycler.set(new SoftReference<>(r));
        }
        return r;
    }

    private BinaryDecoder claimDecoder() {
        return decoderRef.getAndSet(null);
    }

    private BinaryEncoder claimEncoder() {
        return encoderRef.getAndSet(null);
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
