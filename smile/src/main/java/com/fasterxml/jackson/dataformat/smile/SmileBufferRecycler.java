package com.fasterxml.jackson.dataformat.smile;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.util.RecyclerPool;
import com.fasterxml.jackson.core.util.RecyclerPool.WithPool;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator.SharedStringNode;

/**
 * Simple helper class used for implementing simple reuse system for Smile-specific
 * buffers that are used.
 */
public class SmileBufferRecycler
    implements WithPool<SmileBufferRecycler>
{
    public final static int DEFAULT_NAME_BUFFER_LENGTH = 64;

    public final static int DEFAULT_STRING_VALUE_BUFFER_LENGTH = 64;

    // // // Input side

    protected final AtomicReference<String[]> _seenNamesReadBuffer = new AtomicReference<>();

    protected final AtomicReference<String[]> _seenStringValuesReadBuffer = new AtomicReference<>();

    // // // Output side

    protected final AtomicReference<SharedStringNode[]> _seenNamesWriteBuffer = new AtomicReference<>();

    protected final AtomicReference<SharedStringNode[]> _seenStringValuesWriteBuffer = new AtomicReference<>();

    protected RecyclerPool<SmileBufferRecycler> _pool;

    public SmileBufferRecycler() { }

    // // // Input side
    
    public String[] allocSeenNamesReadBuffer() {
        return _seenNamesReadBuffer.getAndSet(null);
    }

    public String[] allocSeenStringValuesReadBuffer() {
        return _seenStringValuesReadBuffer.getAndSet(null);
    }

    public void releaseSeenNamesReadBuffer(String[] buffer) {
        _seenNamesReadBuffer.set(buffer);
    }

    public void releaseSeenStringValuesReadBuffer(String[] buffer) {
        _seenStringValuesReadBuffer.set(buffer);
    }

    // // // Output side

    public SharedStringNode[] allocSeenNamesWriteBuffer() {
        return _seenNamesWriteBuffer.getAndSet(null);
    }

    public SharedStringNode[] allocSeenStringValuesWriteBuffer() {
        return _seenStringValuesWriteBuffer.getAndSet(null);
    }

    public void releaseSeenNamesWriteBuffer(SharedStringNode[] buffer) {
        _seenNamesWriteBuffer.set(buffer);
    }

    public void releaseSeenStringValuesWriteBuffer(SharedStringNode[] buffer) {
        _seenStringValuesWriteBuffer.set(buffer);
    }

    // // // Pooling life-cycle
    
    /**
     * Method called by owner of this recycler instance, to provide reference to
     * {@link RecyclerPool} into which instance is to be released (if any)
     *
     * @since 2.16
     */
    @Override
    public SmileBufferRecycler withPool(RecyclerPool<SmileBufferRecycler> pool) {
        if (_pool != null) {
            throw new IllegalStateException("SmileBufferRecycler already linked to pool: "+pool);
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
    public void release() {
        if (_pool != null) {
            RecyclerPool<SmileBufferRecycler> tmpPool = _pool;
            // nullify the reference to the pool in order to avoid the risk of releasing
            // the same BufferRecycler more than once, thus compromising the pool integrity
            _pool = null;
            tmpPool.releasePooled(this);
        }
    }
}
