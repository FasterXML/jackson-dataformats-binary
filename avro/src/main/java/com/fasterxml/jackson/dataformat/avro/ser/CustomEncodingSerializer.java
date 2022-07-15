package com.fasterxml.jackson.dataformat.avro.ser;

import org.apache.avro.reflect.CustomEncoding;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.CustomEncodingWrapper;
import com.fasterxml.jackson.dataformat.avro.schema.VisitorFormatWrapperImpl;

/**
 * Serializes an object using a avro {@link CustomEncoding}
 *
 * @see com.fasterxml.jackson.dataformat.avro.AvroAnnotationIntrospector
 */
public class CustomEncodingSerializer<T> extends ValueSerializer<T> {

    private final CustomEncodingWrapper<T> encoding;

    public CustomEncodingSerializer(CustomEncoding<T> encoding) {
        this.encoding = new CustomEncodingWrapper<>(encoding);
    }

    @Override
    public void serialize(T t, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
        throws JacksonException
    {
        jsonGenerator.writeEmbeddedObject(new CustomEncodingDatum<>(encoding, t));

    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType type)
    {
        if (visitor instanceof VisitorFormatWrapperImpl) {
            ((VisitorFormatWrapperImpl) visitor).expectAvroFormat(new AvroSchema(encoding.getSchema()));
        } else {
            super.acceptJsonFormatVisitor(visitor, type);
        }
    }
}
