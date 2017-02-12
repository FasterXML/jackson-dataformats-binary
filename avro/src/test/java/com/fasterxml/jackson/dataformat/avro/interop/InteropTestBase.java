package com.fasterxml.jackson.dataformat.avro.interop;

import org.apache.avro.Schema;
import org.junit.Before;
import org.junit.runners.Parameterized;

import java.lang.reflect.Type;

import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.apacheDeserialize;
import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.apacheSchema;
import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.apacheSerialize;
import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonDeserialize;
import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonSchema;
import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonSerialize;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * Base class that helps test and verify that Jackson's implementation of Avro is fully compatible with the Apache reference implementation
 *
 * @param <T>
 * @param <V>
 */
public class InteropTestBase<T, V> {
    private interface BiFunction<T, U, V> {
        V apply(T first, U second);
    }

    public static Object[][] getParameters(Type sourceType, Object originalObject, Type targetType) {
        final Schema apacheAvroSchema  = apacheSchema(sourceType);
        final Schema jacksonAvroSchema = jacksonSchema(sourceType);
        /*System.out.println("APACHE SCHEMA:");
        System.out.println(apacheAvroSchema.toString(true));
        System.out.println("\n\nJACKSON SCHEMA:");
        System.out.println(jacksonAvroSchema.toString(true));*/
        BiFunction<Schema, Object, byte[]> apacheSerializer = new BiFunction<Schema, Object, byte[]>() {
            @Override
            public byte[] apply(Schema schema, Object originalObject) {
                return apacheSerialize(schema, originalObject);
            }
        };
        BiFunction<Schema, Object, byte[]> jacksonSerializer = new BiFunction<Schema, Object, byte[]>() {
            @Override
            public byte[] apply(Schema schema, Object originalObject) {
                return jacksonSerialize(schema, originalObject);
            }
        };
        BiFunction<Schema, byte[], Object> apacheDeserializer = new BiFunction<Schema, byte[], Object>() {
            @Override
            public Object apply(Schema schema, byte[] originalObject) {
                return apacheDeserialize(schema, originalObject);
            }
        };
        BiFunction<Schema, byte[], Object> jacksonDeserializer = new BiFunction<Schema, byte[], Object>() {
            @Override
            public Object apply(Schema schema, byte[] originalObject) {
                return jacksonDeserialize(schema, Object.class, originalObject);
            }
        };
        return new Object[][]{
            new Object[]{
                originalObject, apacheAvroSchema, apacheSerializer, jacksonDeserializer, "Apache to Jackson with Apache schema"
            }, new Object[]{
            originalObject, apacheAvroSchema, jacksonSerializer, jacksonDeserializer, "Jackson to Jackson with Apache schema"
        }, new Object[]{
            originalObject, apacheAvroSchema, jacksonSerializer, apacheDeserializer, "Jackson to Apache with Apache schema"
        }, new Object[]{
            originalObject, jacksonAvroSchema, apacheSerializer, jacksonDeserializer, "Apache to Jackson with Jackson schema"
        }, new Object[]{
            originalObject, jacksonAvroSchema, jacksonSerializer, jacksonDeserializer, "Jackson to Jackson with Jackson schema"
        }, new Object[]{
            originalObject, jacksonAvroSchema, jacksonSerializer, apacheDeserializer, "Jackson to Apache with Jackson schema"
        }
        };
    }

    @Parameterized.Parameter(0)
    public T                             expected;
    @Parameterized.Parameter(1)
    public Schema                        schema;
    @Parameterized.Parameter(2)
    public BiFunction<Schema, T, byte[]> serializer;
    @Parameterized.Parameter(3)
    public BiFunction<Schema, byte[], V> deserializer;
    @Parameterized.Parameter(4)
    public String                        name;
    protected V actual = null;

    @Before
    public void testRoundTrip() {
        byte[] intermediate = null;
        try {
            intermediate = serializer.apply(schema, expected);
        } catch (Exception e) {
            throw new AssertionError("Failed to serialize object", e);
        }
        try {
            actual = deserializer.apply(schema, intermediate);
        } catch (Exception e) {
            throw new AssertionError("Failed to deserialize object", e);
        }
        assertThat(actual, is(not(nullValue())));
    }
}
