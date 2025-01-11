package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.*;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.Encoder;
import org.apache.avro.reflect.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;
import com.fasterxml.jackson.dataformat.avro.testsupport.BiFunction;
import com.fasterxml.jackson.dataformat.avro.testsupport.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class AvroEncodeTest extends InteropTestBase {
    static class Wrapper {
        public double precedingValue = 0.18273465;

        @AvroEncode(using = ApacheImplEncoding.class)
        public CustomComponent component;

        public int followingValue = 3456;

        public void setComponent(CustomComponent c) { component = c; }
    }

    static class CustomComponent {
        public int intValue;

        public byte byteValue;

        public short shortValue;

        public String stringValue;

        @Nullable
        public Map<String, ArrayList<Integer>> mapValue;

        @Nullable
        public CustomComponent nestedRecordValue;

        @Nullable
        public Double doubleValue;

        @Nullable
        public Long longValue;

        @AvroEncode(using = UuidAsBytesAvroEncoding.class)
        UUID uuidValue;

        protected CustomComponent() { }
    }

    @SuppressWarnings("unchecked")
    public static class ApacheImplEncoding extends CustomEncoding<CustomComponent> {

        public ApacheImplEncoding() throws IOException {
            schema = ApacheAvroInteropUtil.getApacheSchema(CustomComponent.class);
        }

        @Override
        protected void write(Object datum, Encoder out) throws IOException {
            ReflectData.get().createDatumWriter(schema).write(datum, out);
        }

        @Override
        protected CustomComponent read(Object reuse, Decoder in) throws IOException {
            return (CustomComponent) ReflectData.get().createDatumReader(schema).read(null, in);
        }

    }

    public static class UuidAsBytesAvroEncoding extends CustomEncoding<UUID> {
        public static byte[] asByteArray(UUID uuid) {
            long msb = uuid.getMostSignificantBits();
            long lsb = uuid.getLeastSignificantBits();
            byte[] buffer = new byte[16];
            for (int i = 0; i < 8; i++) {
                buffer[i] = (byte) (msb >>> 8 * (7 - i));
            }
            for (int i = 8; i < 16; i++) {
                buffer[i] = (byte) (lsb >>> 8 * (7 - i));
            }
            return buffer;
        }

        public static UUID toUUID(byte[] byteArray) {
            long msb = 0;
            long lsb = 0;
            for (int i = 0; i < 8; i++) { msb = (msb << 8) | (byteArray[i] & 0xff); }
            for (int i = 8; i < 16; i++) { lsb = (lsb << 8) | (byteArray[i] & 0xff); }
            return new UUID(msb, lsb);
        }

        public UuidAsBytesAvroEncoding() {
            this.schema = SchemaBuilder.unionOf().nullType().and().bytesBuilder().endBytes().endUnion();
        }

        @Override
        public void write(Object datum, Encoder encoder) throws IOException {
            if (datum == null) {
                encoder.writeIndex(0);
                encoder.writeNull();
                return;
            }
            encoder.writeIndex(1);
            encoder.writeBytes(asByteArray((UUID) datum));
        }

        @Override
        public UUID read(Object datum, Decoder decoder) throws IOException {
            try {
                // get index in union
                int index = decoder.readIndex();
                if (index == 1) {
                    // read in 16 bytes of data
                    ByteBuffer b = ByteBuffer.allocate(16);
                    decoder.readBytes(b);
                    // convert
                    UUID uuid = toUUID(b.array());
                    return uuid;
                } else {
                    decoder.readNull();
                    // no uuid present
                    return null;
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Could not decode bytes into UUID", exception);
            }
        }
    }

    protected Wrapper wrapper;
    protected Wrapper result;

    @BeforeEach
    public void setup() throws IOException {
        wrapper = new Wrapper();
        //
        wrapper.setComponent(new CustomComponent());
        wrapper.component.byteValue = (byte) 126;
        wrapper.component.intValue = 897125364;
        wrapper.component.shortValue = (short) -7614;
        Map<String, ArrayList<Integer>> mv = new HashMap<>();
        wrapper.component.mapValue = mv;
        mv.put("birds", new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6)));
        mv.put("cats", new ArrayList<>());
        mv.put("dogs", new ArrayList<>(Arrays.asList(-1234, 56, 6767, 54134, 57, 86)));
        wrapper.component.stringValue = "Hello World!";
        wrapper.component.uuidValue = UUID.randomUUID();

        CustomComponent cc = new CustomComponent();
        cc.byteValue = (byte) 42;
        cc.intValue = 9557748;
        cc.shortValue = (short) -1542;
        cc.doubleValue = Double.POSITIVE_INFINITY;
        cc.longValue = Long.MAX_VALUE;
        cc.stringValue = "Nested Hello World!";
        cc.uuidValue = UUID.randomUUID();
        wrapper.component.nestedRecordValue = cc;
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testByteValue(
        Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
        BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
        throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);

        result = roundTrip(wrapper);
        assertThat(result.component.byteValue).isEqualTo(wrapper.component.byteValue);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testShortValue(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);
        result = roundTrip(wrapper);

        assertThat(result.component.shortValue).isEqualTo(wrapper.component.shortValue);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testStringValue(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);
        result = roundTrip(wrapper);

        assertThat(result.component.stringValue).isEqualTo(wrapper.component.stringValue);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testDoubleValue(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);
        result = roundTrip(wrapper);

        assertThat(result.component.doubleValue).isEqualTo(wrapper.component.doubleValue);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testLongValue(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);
        result = roundTrip(wrapper);

        assertThat(result.component.longValue).isEqualTo(wrapper.component.longValue);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testIntegerValue(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);
        result = roundTrip(wrapper);

        assertThat(result.component.intValue).isEqualTo(wrapper.component.intValue);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testNestedUuidValue(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);
        result = roundTrip(wrapper);

        assertThat(result.component.nestedRecordValue.uuidValue).isEqualTo(wrapper.component.nestedRecordValue.uuidValue);
    }

    @MethodSource("getParameters")
    @ParameterizedTest(name = "{3}")
    public void testUuidValue(
            Function<Type, Schema> schemaFunctor, BiFunction<Schema, Object, byte[]> serializeFunctor,
            BiFunction<Schema, byte[], Object> deserializeFunctor, String combinationName)
            throws IOException
    {
        useParameters(schemaFunctor, serializeFunctor, deserializeFunctor);
        result = roundTrip(wrapper);

        assertThat(result.component.uuidValue).isEqualTo(wrapper.component.uuidValue);
    }
}
