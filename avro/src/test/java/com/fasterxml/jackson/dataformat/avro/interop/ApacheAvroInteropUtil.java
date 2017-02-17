package com.fasterxml.jackson.dataformat.avro.interop;

import avro.shaded.com.google.common.collect.Maps;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import org.apache.avro.Schema;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;

public abstract class ApacheAvroInteropUtil {
    /*
     * Special subclass of ReflectData that knows how to resolve and bind generic types.
     */
    private static final ReflectData PATCHED_AVRO_REFLECT_DATA = new ReflectData() {
        @Override
        protected Schema createSchema(Type type, Map<String, Schema> names) {
        /*
         * Note, we abuse the fact that we can stick whatever we want into "names" and it won't interfere as long as we don't use String
         * keys. To persist and look up type variable information, we watch for ParameterizedTypes, TypeVariables, and Classes with generic
         * superclasses to extract type variable information and store it in the map. This allows full type variable resolution when
         * building a schema from reflection data.
         */
            if (type instanceof ParameterizedType) {
                TypeVariable[] genericParameters = ((Class) ((ParameterizedType) type).getRawType()).getTypeParameters();
                if (genericParameters.length > 0) {
                    Type[] boundParameters = ((ParameterizedType) type).getActualTypeArguments();
                    for (int i = 0; i < boundParameters.length; i++) {
                        ((Map) names).put(genericParameters[i], createSchema(boundParameters[i], Maps.newHashMap(names)));
                    }
                }
            }
            if (type instanceof Class && ((Class) type).getSuperclass() != null) {
                // Raw class may extend a generic superclass
                // extract all the type bindings and add them to the map so they can be returned by the next block
                // Interfaces shouldn't matter here because interfaces can't have fields and avro only looks at fields.
                TypeVariable[] genericParameters = ((Class) type).getSuperclass().getTypeParameters();
                if (genericParameters.length > 0) {
                    Type[] boundParameters = ((ParameterizedType) ((Class) type).getGenericSuperclass()).getActualTypeArguments();
                    for (int i = 0; i < boundParameters.length; i++) {
                        ((Map) names).put(genericParameters[i], createSchema(boundParameters[i], Maps.newHashMap(names)));
                    }
                }
            }
            if (type instanceof TypeVariable) {
                // Should only get here by recursion normally; names should be populated with the schema for this type variable by a previous
                // stack frame
                if (names.containsKey(type)) {
                    return names.get(type);
                }
                // someone fed us an unbound type variable, just fall through to the default behavior
            }
            return super.createSchema(type, names);
        }

        /*
         * Fix bug where avro can't deserialize from its own schema because it decodes byte[] as an array of ints even though it should be
         */
        @Override
        protected String getSchemaName(Object datum) {
            if (datum instanceof byte[]) {
                return Schema.Type.BYTES.getName();
            }
            return super.getSchemaName(datum);
        }
    };

    public static <T> T apacheDeserialize(Schema schema, byte[] data) {
        try {
            Decoder encoder = DecoderFactory.get().binaryDecoder(data, null);
            return (T) PATCHED_AVRO_REFLECT_DATA.createDatumReader(schema).read(null, encoder);
        } catch (IOException e) {
            throw new RuntimeException("Failed Serialization", e);
        }
    }

    public static Schema apacheSchema(Type type) {
        return PATCHED_AVRO_REFLECT_DATA.getSchema(type);
    }

    public static byte[] apacheSerialize(Schema schema, Object object) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Encoder encoder = EncoderFactory.get().binaryEncoder(baos, null);
            PATCHED_AVRO_REFLECT_DATA.createDatumWriter(schema).write(object, encoder);
            encoder.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed Serialization", e);
        }
    }

    public static <T> T jacksonDeserialize(Schema schema, JavaType type, byte[] data) {
        AvroMapper mapper = new AvroMapper();
        try {
            return mapper.readerFor(type).with(new AvroSchema(schema)).readValue(data, 0, data.length);
        } catch (IOException e) {
            throw new RuntimeException("Failed to Deserialize", e);
        }
    }

    public static <T> T jacksonDeserialize(Schema schema, Type type, byte[] data) {
        return jacksonDeserialize(schema, TypeFactory.defaultInstance().constructType(type), data);
    }

    public static Schema jacksonSchema(Type type) {
        AvroMapper mapper = new AvroMapper();
        try {
            return mapper.schemaFor(mapper.constructType(type)).getAvroSchema();
        } catch (JsonMappingException e) {
            throw new RuntimeException("Failed generating schema", e);
        }
    }

    public static byte[] jacksonSerialize(Schema schema, Object object) {
        AvroMapper mapper = new AvroMapper();
        try {
            return mapper.writer().with(new AvroSchema(schema)).writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed Serialization", e);
        }
    }
}
