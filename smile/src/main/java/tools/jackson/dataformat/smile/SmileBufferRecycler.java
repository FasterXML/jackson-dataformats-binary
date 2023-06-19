package tools.jackson.dataformat.smile;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple helper class used for implementing simple reuse system for Smile-specific
 * buffers that are used.
 *
 * @param <T> Type of name entries stored in arrays to recycle
 */
public class SmileBufferRecycler<T>
{
    public final static int DEFAULT_NAME_BUFFER_LENGTH = 64;

    public final static int DEFAULT_STRING_VALUE_BUFFER_LENGTH = 64;

    protected AtomicReference<T[]> _seenNamesBuffer = new AtomicReference<>();

    protected AtomicReference<T[]> _seenStringValuesBuffer = new AtomicReference<>();

    public SmileBufferRecycler() { }

    public T[] allocSeenNamesBuffer()
    {
        return _seenNamesBuffer.getAndSet(null);
    }

    public T[] allocSeenStringValuesBuffer()
    {
        return _seenStringValuesBuffer.getAndSet(null);
    }

    public void releaseSeenNamesBuffer(T[] buffer) {
        _seenNamesBuffer.set(buffer);
    }

    public void releaseSeenStringValuesBuffer(T[] buffer) {
        _seenStringValuesBuffer.set(buffer);
    }
}
