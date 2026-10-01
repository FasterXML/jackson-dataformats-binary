package com.fasterxml.jackson.dataformat.avro.interop.records;

import java.io.IOException;

import org.junit.jupiter.api.TestTemplate;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests serializing primitive array fields on records
 */
public class RecordWithPrimitiveArrayTest extends InteropTestBase
{
    public static class TestRecord {
        public byte[]   byteArrayField      = new byte[0];
        public short[]  shortArrayField     = new short[0];
        public char[]   characterArrayField = new char[0];
        public int[]    integerArrayField   = new int[0];
        public long[]   longArrayField      = new long[0];
        public float[]  floatArrayField     = new float[0];
        public double[] doubleArrayField    = new double[0];
    }

    @TestTemplate
    public void testByteField() throws IOException {
        TestRecord record = new TestRecord();
        record.byteArrayField = new byte[]{1, 0, -1, Byte.MIN_VALUE, Byte.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.byteArrayField).isEqualTo(record.byteArrayField);
    }

    @TestTemplate
    public void testCharacterField() throws IOException {
        TestRecord record = new TestRecord();
        record.characterArrayField = new char[]{1, 0, Character.MIN_VALUE, Character.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.characterArrayField).isEqualTo(record.characterArrayField);
    }

    @TestTemplate
    public void testDoubleField() throws IOException {
        TestRecord record = new TestRecord();
        record.doubleArrayField = new double[]{1, 0, -1, Double.MIN_VALUE, Double.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.doubleArrayField).isEqualTo(record.doubleArrayField);
    }

    @TestTemplate
    public void testFloatField() throws IOException {
        TestRecord record = new TestRecord();
        record.floatArrayField = new float[]{1, 0, -1, Float.MIN_VALUE, Float.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.floatArrayField).isEqualTo(record.floatArrayField);
    }

    @TestTemplate
    public void testInteger() throws IOException {
        TestRecord record = new TestRecord();
        record.integerArrayField = new int[]{1, 0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.integerArrayField).isEqualTo(record.integerArrayField);
    }

    @TestTemplate
    public void testLongField() throws IOException {
        TestRecord record = new TestRecord();
        record.longArrayField = new long[]{1, 0, -1, Long.MIN_VALUE, Long.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.longArrayField).isEqualTo(record.longArrayField);
    }

    @TestTemplate
    public void testShortField() throws IOException {
        TestRecord record = new TestRecord();
        record.shortArrayField = new short[]{1, 0, -1, Short.MIN_VALUE, Short.MAX_VALUE};
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.shortArrayField).isEqualTo(record.shortArrayField);
    }
}
