package com.fasterxml.jackson.dataformat.avro.ser;

import java.io.IOException;

import org.apache.avro.reflect.CustomEncoding;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.CustomEncodingWrapper;
import com.fasterxml.jackson.dataformat.avro.schema.VisitorFormatWrapperImpl;

/**
 * Serializes an object using a avro {@link CustomEncoding}
 *
 * @see com.fasterxml.jackson.dataformat.avro.AvroAnnotationIntrospector
 */
public class CustomEncodingSerializer<T> extends JsonSerializer<T> {

    private final CustomEncodingWrapper<T> encoding;

    public CustomEncodingSerializer(CustomEncoding<T> encoding) {
        this.encoding = new CustomEncodingWrapper<>(encoding);
    }

    @Override
    public void serialize(T t, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
    throws IOException, JsonProcessingException {
        jsonGenerator.writeEmbeddedObject(new CustomEncodingDatum<>(encoding, t));

    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType type) throws JsonMappingException {
        if (visitor instanceof VisitorFormatWrapperImpl) {
            ((VisitorFormatWrapperImpl) visitor).expectAvroFormat(new AvroSchema(encoding.getSchema()));
        } else {
            super.acceptJsonFormatVisitor(visitor, type);
        }
    }
}
