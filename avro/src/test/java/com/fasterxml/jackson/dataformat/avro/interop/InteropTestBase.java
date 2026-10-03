package com.fasterxml.jackson.dataformat.avro.interop;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.apache.avro.Schema;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

import com.fasterxml.jackson.dataformat.avro.testsupport.BiFunction;
import com.fasterxml.jackson.dataformat.avro.testsupport.Function;

import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.*;

/**
 * Parameterized base class for tests that populates {@link #schemaFunctor}, {@link #serializeFunctor}, and
 * {@link #deserializeFunctor} with permutations of Apache and Jackson implementations to test all aspects of
 * interoperability between the implementations.
 */
@ExtendWith(InteropTestBase.CombinationsProvider.class)
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

    static class ParameterizedTypeImpl implements ParameterizedType {
        private final Class<?> rawType;
        private final Type[]   typeBindings;

        ParameterizedTypeImpl(Class<?> rawType, Type[] typeBindings) {
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

    public Function<Type, Schema> schemaFunctor;
    public BiFunction<Schema, Object, byte[]> serializeFunctor;
    public BiFunction<Schema, byte[], Object> deserializeFunctor;
    public String combinationName;

    /**
     * Test methods of subclasses are "test templates" (annotated with {@code @TestTemplate}):
     * invoked once for each combination returned by {@link #getParameters()}, with
     * fields of the test instance populated from combination values.
     */
    static class CombinationsProvider implements TestTemplateInvocationContextProvider
    {
        @Override
        public boolean supportsTestTemplate(ExtensionContext context) {
            return true;
        }

        @Override
        public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context)
        {
            return Stream.of(getParameters()).map(CombinationContext::new);
        }
    }

    @SuppressWarnings("unchecked")
    static class CombinationContext implements TestTemplateInvocationContext, TestInstancePostProcessor
    {
        private final Object[] _params;

        CombinationContext(Object[] params) {
            _params = params;
        }

        @Override
        public String getDisplayName(int invocationIndex) {
            return (String) _params[3];
        }

        @Override
        public List<org.junit.jupiter.api.extension.Extension> getAdditionalExtensions() {
            return Collections.<org.junit.jupiter.api.extension.Extension>singletonList(this);
        }

        @Override
        public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
            InteropTestBase test = (InteropTestBase) testInstance;
            test.schemaFunctor = (Function<Type, Schema>) _params[0];
            test.serializeFunctor = (BiFunction<Schema, Object, byte[]>) _params[1];
            test.deserializeFunctor = (BiFunction<Schema, byte[], Object>) _params[2];
            test.combinationName = (String) _params[3];
        }
    }

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
