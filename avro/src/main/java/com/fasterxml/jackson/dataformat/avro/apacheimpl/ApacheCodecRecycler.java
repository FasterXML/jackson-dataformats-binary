package com.fasterxml.jackson.dataformat.avro.apacheimpl;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;

import com.fasterxml.jackson.core.util.RecyclerPool;
import com.fasterxml.jackson.core.util.RecyclerPool.WithPool;

/**
 * Simple helper class that contains extracted functionality for
 * simple encoder/decoder recycling.
 *
 * @since 2.8.7
 */
public final class ApacheCodecRecycler
    implements WithPool<ApacheCodecRecycler>
{
    // NOTE: AtomicReference only needed for ThreadLocal recycling where
    //  single-thread access is not (ironically enough) ensured
    private final AtomicReference<BinaryDecoder> decoderRef = new AtomicReference<>();

    // NOTE: AtomicReference only needed for ThreadLocal recycling where
    //  single-thread access is not (ironically enough) ensured
    private final AtomicReference<BinaryEncoder> encoderRef = new AtomicReference<>();

    private RecyclerPool<ApacheCodecRecycler> _pool;

    ApacheCodecRecycler() { }

    /*
    /**********************************************************
    /* Public API
    /**********************************************************
     */

    public BinaryDecoder acquireDecoder() {
        return decoderRef.getAndSet(null);
    }

    public BinaryEncoder acquireEncoder() {
        return encoderRef.getAndSet(null);
    }

    public void release(BinaryDecoder dec) {
        decoderRef.set(dec);
    }

    public void release(BinaryEncoder enc) {
        encoderRef.set(enc);
    }

    /*
    /**********************************************************
    /* WithPool implementation
    /**********************************************************
     */
    
    /**
     * Method called by owner of this recycler instance, to provide reference to
     * {@link RecyclerPool} into which instance is to be released (if any)
     *
     * @since 2.16
     */
    @Override
    public ApacheCodecRecycler withPool(RecyclerPool<ApacheCodecRecycler> pool) {
        if (this._pool != null) {
            throw new IllegalStateException("ApacheCodecRecycler already linked to pool: "+pool);
        }
        // assign to pool to which this BufferRecycler belongs in order to release it
        // to the same pool when the work will be completed
        _pool = Objects.requireNonNull(pool);
        return this;
    }

    /**
     * Method called when owner of this recycler no longer wishes use it; this should
     * return it to pool passed via {@code withPool()} (if any).
     *
     * @since 2.16
     */
    @Override
    public void releaseToPool() {
        if (_pool != null) {
            RecyclerPool<ApacheCodecRecycler> tmpPool = _pool;
            // nullify the reference to the pool in order to avoid the risk of releasing
            // the same BufferRecycler more than once, thus compromising the pool integrity
            _pool = null;
            tmpPool.releasePooled(this);
        }
    }
}
