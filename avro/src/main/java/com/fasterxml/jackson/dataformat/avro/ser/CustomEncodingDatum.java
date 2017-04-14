package com.fasterxml.jackson.dataformat.avro.ser;

import java.io.IOException;

import org.apache.avro.io.Encoder;

import com.fasterxml.jackson.dataformat.avro.CustomEncodingWrapper;

/**
 * Writes out an object using a {@link org.apache.avro.reflect.CustomEncoding}
 *
 * @param <T> Type of data supported by this EncodedDatum
 */
public class CustomEncodingDatum<T> implements EncodedDatum {

    private final CustomEncodingWrapper<T> _encoding;

    private final T _datum;

    public CustomEncodingDatum(CustomEncodingWrapper<T> encoding, T datum) {
        this._encoding = encoding;
        this._datum = datum;
    }

    @Override
    public void write(Encoder encoder) throws IOException {
        _encoding.write(_datum, encoder);
    }
}
