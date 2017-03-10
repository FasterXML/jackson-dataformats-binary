package com.fasterxml.jackson.dataformat.avro.interop.records;

import lombok.Data;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests serializing wrapper types for primitives on records
 */
public class RecordWithPrimitiveWrapperTest extends InteropTestBase
{
    @Data
    public static class TestRecord {
        private Byte      byteField      = 0;
        private Short     shortField     = 0;
        private Character characterField = 'A';
        private Integer   integerField   = 0;
        private Long      longField      = 0L;
        private Float     floatField     = 0F;
        private Double    doubleField    = 0D;
        private String    stringField    = "";
    }

    @Before
    public void setup() {
        // 2.8 doesn't generate schemas with compatible namespaces for Apache deserializer
        assumeCompatibleNsForDeser();
    }
    
    @Test
    public void testByteField() {
        TestRecord record = new TestRecord();
        record.byteField = Byte.MAX_VALUE;
        TestRecord result = roundTrip(record);
        assertThat(result.byteField).isEqualTo(record.byteField);
    }

    @Test
    public void testCharacterField() {
        TestRecord record = new TestRecord();
        record.characterField = Character.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.characterField).isEqualTo(record.characterField);
    }

    @Test
    public void testDoubleField() {
        TestRecord record = new TestRecord();
        record.doubleField = Double.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.doubleField).isEqualTo(record.doubleField);
    }

    @Test
    public void testFloatField() {
        TestRecord record = new TestRecord();
        record.floatField = Float.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.floatField).isEqualTo(record.floatField);
    }

    @Test
    public void testInteger() {
        TestRecord record = new TestRecord();
        record.integerField = Integer.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.integerField).isEqualTo(record.integerField);
    }

    @Test
    public void testLongField() {
        TestRecord record = new TestRecord();
        record.longField = Long.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.longField).isEqualTo(record.longField);
    }

    @Test
    public void testShortField() {
        TestRecord record = new TestRecord();
        record.shortField = Short.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.shortField).isEqualTo(record.shortField);
    }

    @Test
    public void testStringField() {
        TestRecord record = new TestRecord();
        record.stringField = "Hello World";
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.stringField).isEqualTo(record.stringField);
    }
}
