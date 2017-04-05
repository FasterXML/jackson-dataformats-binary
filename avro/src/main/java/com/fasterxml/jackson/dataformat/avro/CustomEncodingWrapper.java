package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.avro.Schema;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.Encoder;
import org.apache.avro.reflect.CustomEncoding;

/**
 * Wrapper that makes the methods on a {@link CustomEncoding} accessible since they are otherwise package-private.
 */
public class CustomEncodingWrapper<T> {
    private static final Method GET_SCHEMA;
    private static final Method READ;
    private static final Method WRITE;

    static {
        try {
            GET_SCHEMA = CustomEncoding.class.getDeclaredMethod("getSchema");
            READ = CustomEncoding.class.getDeclaredMethod("read", Object.class, Decoder.class);
            WRITE = CustomEncoding.class.getDeclaredMethod("write", Object.class, Encoder.class);
            GET_SCHEMA.setAccessible(true);
            READ.setAccessible(true);
            WRITE.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to initialize CustomEncoderWrapper, Avro version mismatch?", e);
        }
    }

    private final CustomEncoding<T> encoding;

    public CustomEncodingWrapper(CustomEncoding<T> encoding) {
        this.encoding = encoding;
    }

    public void write(Object datum, Encoder out) throws IOException {
        try {
            WRITE.invoke(encoding, datum, out);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to encode object", e);
        }
    }

    public Schema getSchema() {
        try {
            return (Schema) GET_SCHEMA.invoke(encoding);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to access schema", e);
        }
    }

    @SuppressWarnings("unchecked")
    public T read(Object reuse, Decoder in) throws IOException {
        try {
            return (T) READ.invoke(encoding, reuse, in);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to decode object", e);
        }
    }
}
