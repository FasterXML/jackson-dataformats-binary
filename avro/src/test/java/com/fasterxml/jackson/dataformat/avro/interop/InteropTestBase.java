package com.fasterxml.jackson.dataformat.avro.interop;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.apache.avro.Schema;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.fasterxml.jackson.dataformat.avro.testsupport.BiFunction;
import com.fasterxml.jackson.dataformat.avro.testsupport.Function;

import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.*;

/**
 * Parameterized base class for tests that populates {@link #schemaFunctor}, {@link #serializeFunctor}, and
 * {@link #deserializeFunctor} with permutations of Apache and Jackson implementations to test all aspects of
 * interoperability between the implementations.
 */
@RunWith(Parameterized.class)
public abstract class InteropTestBase
{
    public enum DummyEnum {
        NORTH, SOUTH, EAST, WEST
    }

    /**
     * Helper method for building a {@link ParameterizedType} for use with <code>roundTrip(Type, Object)</code>
     *
     * @param baseClass
     *     A generic {@link Class} with type variables
     * @param parameters
     *     Bindings for the variables in {@code baseClass}
     *
     * @return A type representing the bound {@code baseClass}
     */
    public static ParameterizedType type(Class<?> baseClass, Type... parameters) {
        if (baseClass.getTypeParameters().length != parameters.length) {
            throw new IllegalArgumentException("Incorrect number of type parameters, expected "
                                               + baseClass.getTypeParameters().length
                                               + ", got "
                                               + parameters.length);
        }
        for (Type type : parameters) {
            if (!(type instanceof Class) && !(type instanceof ParameterizedType)) {
                throw new IllegalArgumentException("Only Class and ParameterizedType bindings are supported");
            }
        }
        return new ParameterizedTypeImpl(baseClass, parameters);
    }

    private static class ParameterizedTypeImpl implements ParameterizedType {
        private final Class<?> rawType;
        private final Type[]   typeBindings;

        private ParameterizedTypeImpl(Class<?> rawType, Type[] typeBindings) {
            this.rawType = rawType;
            this.typeBindings = typeBindings;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return typeBindings;
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return rawType.getEnclosingClass();
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(rawType.getName());
            if (typeBindings.length != 0) {
                builder.append('<');
                for (Type type : typeBindings) {
                    if (type instanceof Class<?>) {
                        builder.append(((Class<?>) type).getName());
                    } else {
                        builder.append(type.toString());
                    }
                }
                builder.append('>');
            }
            return builder.toString();
        }
    }

    @Parameterized.Parameter
    public Function<Type, Schema> schemaFunctor;
    @Parameterized.Parameter(1)
    public BiFunction<Schema, Object, byte[]> serializeFunctor;
    @Parameterized.Parameter(2)
    public BiFunction<Schema, byte[], Object> deserializeFunctor;
    @Parameterized.Parameter(3)
    public String combinationName;

    @Parameterized.Parameters(name = "{3}")
    public static Object[][] getParameters() {
        return new Object[][]{
                {getApacheSchema, apacheSerializer, jacksonDeserializer, "Apache to Jackson with Apache schema"},
                {getJacksonSchema, apacheSerializer, jacksonDeserializer, "Apache to Jackson with Jackson schema"},
                {getApacheSchema, jacksonSerializer, jacksonDeserializer, "Jackson to Jackson with Apache schema"},
                {getJacksonSchema, jacksonSerializer, jacksonDeserializer, "Jackson to Jackson with Jackson schema"},
                {getApacheSchema, jacksonSerializer, apacheDeserializer, "Jackson to Apache with Apache schema"},
                {getJacksonSchema, jacksonSerializer, apacheDeserializer, "Jackson to Apache with Jackson schema"},
                {getJacksonSchema, apacheSerializer, apacheDeserializer, "Apache to Apache with Jackson schema"},
                {getApacheSchema, apacheSerializer, apacheDeserializer, "Apache to Apache with Apache schema"}
        };
    }

    /**
     * Serializes and deserializes the {@code object} using the current combination of schema generator, serializer, and
     * deserializer implementations
     *
     * @param object
     *     The object to serialize and deserialize. The schema used for serialization and deserialization will be generated based on {@code
     *     object.getClass()}.
     * @param <T>
     *     Type of object being serialized and deserialized
     *
     * @return A recreated version of the original object
     */
    protected <T> T roundTrip(T object) throws IOException {
        return roundTrip(object.getClass(), object);
    }

    /**
     * Serializes and deserializes the {@code object} using the current combination of schema generator, serializer, and
     * deserializer implementations
     *
     * @param schemaType
     *     Type to use for generating the schema when {@code object} has
     * @param object
     *     The object to serialize and deserialize. The schema used for serialization and deserialization will be generated based on {@code
     *     object.getClass()}.
     * @param <T>
     *     Type of object being serialized and deserialized
     *
     * @return A recreated version of the original object
     */
    @SuppressWarnings("unchecked")
    protected <T> T roundTrip(Type schemaType, T object) throws IOException {
        Schema schema = schemaFunctor.apply(schemaType);
        return (T) deserializeFunctor.apply(schema, serializeFunctor.apply(schema, object));
    }
}
