package com.fasterxml.jackson.dataformat.avro.apacheimpl;

import java.io.IOException;

import org.apache.avro.reflect.CustomEncoding;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.WrappedIOException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import com.fasterxml.jackson.dataformat.avro.CustomEncodingWrapper;
import com.fasterxml.jackson.dataformat.avro.deser.AvroParserImpl;

/**
 * Deserializes an object using a avro {@link CustomEncoding}
 *
 * @see com.fasterxml.jackson.dataformat.avro.AvroAnnotationIntrospector
 */
public class CustomEncodingDeserializer<T> extends ValueDeserializer<T> {

    private final CustomEncodingWrapper<T> encoding;

    public CustomEncodingDeserializer(CustomEncoding<T> encoding) {
        this.encoding = new CustomEncodingWrapper<>(encoding);
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        AvroParserImpl avroParser = (AvroParserImpl) p;
        DecoderOverAvroParser decoder = new DecoderOverAvroParser(avroParser);
        try {
            return encoding.read(null, decoder);
        } catch (IOException e) {
            throw WrappedIOException.construct(e);
        }
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt, T intoValue) throws JacksonException {
        AvroParserImpl avroParser = (AvroParserImpl) p;
        DecoderOverAvroParser decoder = new DecoderOverAvroParser(avroParser);
        try {
            return encoding.read(intoValue, decoder);
        } catch (IOException e) {
            throw WrappedIOException.construct(e);
        }
    }
}
