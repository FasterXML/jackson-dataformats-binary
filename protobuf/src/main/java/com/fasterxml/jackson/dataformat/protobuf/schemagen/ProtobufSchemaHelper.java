package com.fasterxml.jackson.dataformat.protobuf.schemagen;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.util.ClassUtil;
import com.squareup.protoparser.DataType;
import com.squareup.protoparser.DataType.ScalarType;

public class ProtobufSchemaHelper
{
    private ProtobufSchemaHelper(){}
	
    public static String getNamespace(JavaType type) {
        Class<?> cls = type.getRawClass();
        Package pkg = cls.getPackage();
        return (pkg == null) ? "" : pkg.getName();
    }

    public static ScalarType getScalarType(JavaType type) {
        Class<?> raw = type.getRawClass();
        if (raw.isPrimitive()) {
            raw = ClassUtil.wrapperType(raw);
        }
        if ((raw == Integer.class)
                // 29-Mar-2017, tatu: Also shorter types...
                || (raw == Short.class)
                || (raw == Byte.class)) {
            return DataType.ScalarType.INT32;
        }
        if ((raw == Long.class) || (raw == BigInteger.class)) {
            return DataType.ScalarType.INT64;
        }
        if (raw == String.class) {
            return DataType.ScalarType.STRING;
        }
        if (raw == Float.class) {
            return DataType.ScalarType.FLOAT;
        }
        if (raw == Boolean.class) {
            return DataType.ScalarType.BOOL;
        }
        if ((raw == byte[].class) || (raw == ByteBuffer.class)) {
            return DataType.ScalarType.BYTES;
        }
        if ((raw == Double.class) || (raw == BigDecimal.class)) {
            // is this right wrt BigDecimal?
            return DataType.ScalarType.DOUBLE;
        }
        return null;
    }

    public static boolean hasIndex(BeanProperty writer) {
        return writer.getMetadata().hasIndex();
    }

    public static TypeElementBuilder acceptTypeElement(SerializerProvider provider, JavaType type,
            DefinedTypeElementBuilders definedTypeElementBuilders, boolean isNested) throws JsonMappingException {
        JsonSerializer<Object> serializer = provider.findValueSerializer(type, null);
        ProtoBufSchemaVisitor visitor = new ProtoBufSchemaVisitor(provider, definedTypeElementBuilders, isNested);
        serializer.acceptJsonFormatVisitor(visitor, type);
        return visitor;
    }
}
