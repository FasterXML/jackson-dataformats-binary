package com.fasterxml.jackson.dataformat.avro.interop.records;

import lombok.Data;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests serializing primitive array fields on records
 */
public class RecordWithPrimitiveArrayTest extends InteropTestBase
{
    @Before
    public void setup() {
        // 2.8 doesn't generate schemas with compatible namespaces for Apache deserializer
        assumeCompatibleNsForDeser();
    }

    @Data
    public static class TestRecord {
        private byte[]   byteArrayField      = new byte[0];
        private short[]  shortArrayField     = new short[0];
        private char[]   characterArrayField = new char[0];
        private int[]    integerArrayField   = new int[0];
        private long[]   longArrayField      = new long[0];
        private float[]  floatArrayField     = new float[0];
        private double[] doubleArrayField    = new double[0];
    }

    @Test
    public void testByteField() {
        TestRecord record = new TestRecord();
        record.byteArrayField = new byte[]{1, 0, -1, Byte.MIN_VALUE, Byte.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.byteArrayField).isEqualTo(record.byteArrayField);
    }

    @Test
    public void testCharacterField() {
        TestRecord record = new TestRecord();
        record.characterArrayField = new char[]{1, 0, Character.MIN_VALUE, Character.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.characterArrayField).isEqualTo(record.characterArrayField);
    }

    @Test
    public void testDoubleField() {
        TestRecord record = new TestRecord();
        record.doubleArrayField = new double[]{1, 0, -1, Double.MIN_VALUE, Double.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.doubleArrayField).isEqualTo(record.doubleArrayField);
    }

    @Test
    public void testFloatField() {
        TestRecord record = new TestRecord();
        record.floatArrayField = new float[]{1, 0, -1, Float.MIN_VALUE, Float.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.floatArrayField).isEqualTo(record.floatArrayField);
    }

    @Test
    public void testInteger() {
        TestRecord record = new TestRecord();
        record.integerArrayField = new int[]{1, 0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.integerArrayField).isEqualTo(record.integerArrayField);
    }

    @Test
    public void testLongField() {
        TestRecord record = new TestRecord();
        record.longArrayField = new long[]{1, 0, -1, Long.MIN_VALUE, Long.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.longArrayField).isEqualTo(record.longArrayField);
    }

    @Test
    public void testShortField() {
        TestRecord record = new TestRecord();
        record.shortArrayField = new short[]{1, 0, -1, Short.MIN_VALUE, Short.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.shortArrayField).isEqualTo(record.shortArrayField);
    }
}
