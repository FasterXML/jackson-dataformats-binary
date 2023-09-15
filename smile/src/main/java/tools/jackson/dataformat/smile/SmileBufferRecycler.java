package tools.jackson.dataformat.smile;

import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.dataformat.smile.SmileGenerator.SharedStringNode;

/**
 * Simple helper class used for implementing simple reuse system for Smile-specific
 * buffers that are used.
 */
public class SmileBufferRecycler
{
    public final static int DEFAULT_NAME_BUFFER_LENGTH = 64;

    public final static int DEFAULT_STRING_VALUE_BUFFER_LENGTH = 64;

    // // // Input side

    protected final AtomicReference<String[]> _seenNamesReadBuffer = new AtomicReference<>();

    protected final AtomicReference<String[]> _seenStringValuesReadBuffer = new AtomicReference<>();

    // // // Output side

    protected final AtomicReference<SharedStringNode[]> _seenNamesWriteBuffer = new AtomicReference<>();

    protected final AtomicReference<SharedStringNode[]> _seenStringValuesWriteBuffer = new AtomicReference<>();


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
}
