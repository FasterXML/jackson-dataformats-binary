package com.fasterxml.jackson.dataformat.avro.interop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectData;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroModule;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.testsupport.BiFunction;
import com.fasterxml.jackson.dataformat.avro.testsupport.Function;

/**
 * Utilities and helper functions to aid compatibility testing between Jackson and Apache Avro implementations
 */
public class ApacheAvroInteropUtil {
    /**
     * Functor of {@link #jacksonSerialize(Schema, Object)}
     */
    public static final BiFunction<Schema, Object, byte[]> jacksonSerializer = new BiFunction<Schema, Object, byte[]>() {
        @Override
        public byte[] apply(Schema schema, Object originalObject) throws IOException {
            return jacksonSerialize(schema, originalObject);
        }
    };
    /**
     * Functor of {@link #getJacksonSchema(Type)}
     */
    public static final Function<Type, Schema> getJacksonSchema = new Function<Type, Schema>() {
        @Override
        public Schema apply(Type input) throws IOException {
            return getJacksonSchema(input);
        }
    };
    /**
     * Functor of {@link #jacksonDeserialize(Schema, Type, byte[])} which uses {@link Object} as the target type,
     * requiring the use of native type IDs
     */
    public static final  BiFunction<Schema, byte[], Object> jacksonDeserializer = new BiFunction<Schema, byte[], Object>() {
        @Override
        public Object apply(Schema schema, byte[] originalObject) throws IOException {
            return jacksonDeserialize(schema, Object.class, originalObject);
        }
    };
    /**
     * Functor of {@link #getApacheSchema(Type)}
     */
    public static final  Function<Type, Schema> getApacheSchema = new Function<Type, Schema>() {
        @Override
        public Schema apply(Type input) {
            return getApacheSchema(input);
        }
    };
    /**
     * Functor of {@link #apacheDeserialize(Schema, byte[])}
     */
    public static final BiFunction<Schema, byte[], Object> apacheDeserializer = new BiFunction<Schema, byte[], Object>() {
        @Override
        public Object apply(Schema first, byte[] second) throws IOException {
            return apacheDeserialize(first, second);
        }
    };
    /**
     * Functor of {@link #apacheSerialize(Schema, Object)}
     */
    public static final  BiFunction<Schema, Object, byte[]> apacheSerializer = new BiFunction<Schema, Object, byte[]>() {
        @Override
        public byte[] apply(Schema schema, Object originalObject) throws IOException {
            return apacheSerialize(schema, originalObject);
        }
    };

    private static final AvroMapper MAPPER = new AvroMapper(new AvroModule());

    /*
     * Special subclass of ReflectData that knows how to resolve and bind generic types. This saves us much pain of
     * having to build these schemas by hand. Also, workarounds for several bugs in the Apache implementation are
     * implemented here.
     */
    private static final ReflectData PATCHED_AVRO_REFLECT_DATA = new ReflectData() {
        @SuppressWarnings({"unchecked", "SuspiciousMethodCalls", "rawtypes"})
        @Override
        protected Schema createSchema(Type type, Map<String, Schema> names) {
        /* Note, we abuse the fact that we can stick whatever we want into "names" and it won't interfere as long as we don't use String
         * keys. To persist and look up type variable information, we watch for ParameterizedTypes, TypeVariables, and Classes with
         * generic superclasses to extract type variable information and store it in the map. This allows full type variable resolution
         * when building a schema from reflection data.
         */
            if (type instanceof ParameterizedType) {
                TypeVariable<?>[] genericParameters = ((Class<?>) ((ParameterizedType) type).getRawType()).getTypeParameters();
                if (genericParameters.length > 0) {
                    Type[] boundParameters = ((ParameterizedType) type).getActualTypeArguments();
                    for (int i = 0; i < boundParameters.length; i++) {
                        ((Map) names).put(genericParameters[i], createSchema(boundParameters[i], new HashMap<>(names)));
                    }
                }
            }
            if (type instanceof Class<?> && ((Class<?>) type).getSuperclass() != null && !Enum.class.isAssignableFrom((Class<?>) type)) {
                // Raw class may extend a generic superclass
                // extract all the type bindings and add them to the map so they can be returned by the next block
                // Interfaces shouldn't matter here because interfaces can't have fields and avro only looks at fields.
                TypeVariable<?>[] genericParameters = ((Class<?>) type).getSuperclass().getTypeParameters();
                if (genericParameters.length > 0) {
                    Type[] boundParameters = ((ParameterizedType) ((Class<?>) type).getGenericSuperclass()).getActualTypeArguments();
                    for (int i = 0; i < boundParameters.length; i++) {
                        ((Map) names).put(genericParameters[i], createSchema(boundParameters[i], new HashMap<>(names)));
                    }
                }
            }
            if (type instanceof TypeVariable) {
                // Should only get here by recursion normally; names should be populated with the schema for this type variable by a
                // previous stack frame
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

    /**
     * Deserialize an avro-encoded payload using the given {@code schema} and Apache implementation
     *
     * @param schema
     *     {@link Schema} to use when deserializing the payload
     * @param data
     *     Payload to deserialize
     * @param <T>
     *     Expected type of the deserialized payload
     *
     * @return Deserialized payload
     */
    @SuppressWarnings("unchecked")
    public static <T> T apacheDeserialize(Schema schema, byte[] data) throws IOException {
        Decoder encoder = DecoderFactory.get().binaryDecoder(data, null);
        return (T) PATCHED_AVRO_REFLECT_DATA.createDatumReader(schema).read(null, encoder);
    }

    /**
     * Serializes the {@code object} using the given {@code schema} and the Apache implementation
     *
     * @param schema
     *     {@link Schema} to use when serializing the {@code object}
     * @param object
     *     Object to serialize
     *
     * @return Payload containing the Avro-serialized form of {@code object}
     */
    @SuppressWarnings("unchecked")
    public static byte[] apacheSerialize(Schema schema, Object object) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Encoder encoder = EncoderFactory.get().binaryEncoder(baos, null);
        PATCHED_AVRO_REFLECT_DATA.createDatumWriter(schema).write(object, encoder);
        encoder.flush();
        return baos.toByteArray();
    }

    /**
     * Generates a {@link Schema} for {@code type} using the Apache implementation of Avro
     *
     * @param type
     *     Type for which schema should be generated
     *
     * @return Schema for {@code type}
     */
    public static Schema getApacheSchema(Type type) {
        return PATCHED_AVRO_REFLECT_DATA.getSchema(type);
    }

    /**
     * Generates a {@link Schema} for {@code type} using the Jackson implementation of Avro
     *
     * @param type
     *     Type for which schema should be generated
     *
     * @return Schema for {@code type}
     */
    public static Schema getJacksonSchema(Type type) throws IOException {
        return MAPPER.schemaFor(MAPPER.constructType(type)).getAvroSchema();
    }

    /**
     * Deserialize an avro-encoded payload using the given {@code schema} and Jackson implementation, using the given target {@code type}
     *
     * @param schema
     *     {@link Schema} to use when deserializing the payload
     * @param type
     *     Target type into which Jackson will deserialize the payload
     * @param data
     *     Payload to deserialize
     * @param <T>
     *     Expected type of the deserialized payload
     *
     * @return Deserialized payload
     */
    public static <T> T jacksonDeserialize(Schema schema, JavaType type, byte[] data) throws IOException {
        return MAPPER.readerFor(type).with(new AvroSchema(schema)).readValue(data, 0, data.length);
    }

    /**
     * Deserialize an avro-encoded payload using the given {@code schema} and Jackson implementation, using the given target {@code type}
     *
     * @param schema
     *     {@link Schema} to use when deserializing the payload
     * @param type
     *     Target type into which Jackson will deserialize the payload
     * @param data
     *     Payload to deserialize
     * @param <T>
     *     Expected type of the deserialized payload
     *
     * @return Deserialized payload
     */
    public static <T> T jacksonDeserialize(Schema schema, Type type, byte[] data) throws IOException {
        return jacksonDeserialize(schema, MAPPER.constructType(type), data);
    }

    /**
     * Serializes the {@code object} using the given {@code schema} and the Jackson implementation
     *
     * @param schema
     *     {@link Schema} to use when serializing the {@code object}
     * @param object
     *     Object to serialize
     *
     * @return Payload containing the Avro-serialized form of {@code object}
     */
    public static byte[] jacksonSerialize(Schema schema, Object object) throws IOException {
        return MAPPER.writer().with(new AvroSchema(schema)).writeValueAsBytes(object);
    }
}
