package com.fasterxml.jackson.dataformat.avro.apacheimpl;

import java.io.IOException;

import org.apache.avro.reflect.CustomEncoding;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.dataformat.avro.CustomEncodingWrapper;
import com.fasterxml.jackson.dataformat.avro.deser.AvroParserImpl;

/**
 * Deserializes an object using a avro {@link CustomEncoding}
 *
 * @see com.fasterxml.jackson.dataformat.avro.AvroAnnotationIntrospector
 */
public class CustomEncodingDeserializer<T> extends JsonDeserializer<T> {

    private final CustomEncodingWrapper<T> encoding;

    public CustomEncodingDeserializer(CustomEncoding<T> encoding) {
        this.encoding = new CustomEncodingWrapper<>(encoding);
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        AvroParserImpl avroParser = (AvroParserImpl) p;
        DecoderOverAvroParser decoder = new DecoderOverAvroParser(avroParser);
        return encoding.read(null, decoder);
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt, T intoValue) throws IOException {
        AvroParserImpl avroParser = (AvroParserImpl) p;
        DecoderOverAvroParser decoder = new DecoderOverAvroParser(avroParser);
        return encoding.read(intoValue, decoder);
    }
}
