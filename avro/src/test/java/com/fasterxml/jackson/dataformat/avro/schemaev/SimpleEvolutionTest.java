package com.fasterxml.jackson.dataformat.avro.schemaev;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.SequenceWriter;

import com.fasterxml.jackson.dataformat.avro.*;
import com.fasterxml.jackson.dataformat.avro.testsupport.LimitingInputStream;

public class SimpleEvolutionTest extends AvroTestBase
{
    // NOTE! Avro requires named types to match; this is why type names
    //   are identical despite differences in field composition...

    static String SCHEMA_X_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'EvolvingPoint',\n"+
            " 'fields':[\n"+
            "    { 'name':'x', 'type':'int' }\n"+
            " ]\n"+
            "}\n");

    static String SCHEMA_XY_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'EvolvingPoint',\n"+
            " 'fields':[\n"+
            "    { 'name':'x', 'type':'int' },\n"+
            "    { 'name':'y', 'type':'int' }\n"+
            " ]\n"+
            "}\n");

    static String SCHEMA_XYZ_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'EvolvingPoint',\n"+
            " 'fields':[\n"+
            "    { 'name':'z', 'type':'int', 'default': 99 },\n"+
            "    { 'name':'y', 'type':'int' },\n"+
            "    { 'name':'x', 'type':'int' }\n"+
            " ]\n"+
            "}\n");

    static String SCHEMA_XYZ_RENAMED_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'EvolvingPoint2',\n"+
            " 'fields':[\n"+
            "    { 'name':'z', 'type':'int', 'default': 99 },\n"+
            "    { 'name':'y', 'type':'int' },\n"+
            "    { 'name':'x', 'type':'int' }\n"+
            " ]\n"+
            "}\n");
    
    // and one with X + T and an Array
    static String SCHEMA_XAY_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'EvolvingPoint',\n"+
            " 'fields':[\n"+
            "    { 'name':'x', 'type':'int' },\n"+
            "    { 'name':'a', 'type': {\n"+
            "       'type':'array', 'items': 'int'\n"+
            "      }\n"+
            "    },\n"+
            "    { 'name':'y', 'type':'int' }\n"+
            " ]\n"+
            "}\n");

    // then one with X + T and an Array
    static String SCHEMA_XBY_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'EvolvingPoint',\n"+
            " 'fields':[\n"+
            "    { 'name':'x', 'type':'int' },\n"+
            "    { 'name':'binary', 'type':'bytes' },\n"+
            "    { 'name':'y', 'type':'int' }\n"+
            " ]\n"+
            "}\n");
    
    static class PointXY {
        public int x, y;

        protected PointXY() { }
        public PointXY(int x0, int y0) {
            x = x0;
            y = y0;
        }

        @Override
        public boolean equals(Object o) {
            PointXY other = (PointXY) o;
            return (x == other.x) && (y == other.y);
        }

        @Override
        public String toString() {
            return String.format("[%d,%d]", x, y);
        }
    }

    static class PointXYZ {
        public int x, y, z;

        protected PointXYZ() { }
        public PointXYZ(int x0, int y0, int z0) {
            x = x0;
            y = y0;
            z = z0;
        }

        @Override
        public boolean equals(Object o) {
            PointXYZ other = (PointXYZ) o;
            return (x == other.x) && (y == other.y)  && (z == other.z);
        }

        @Override
        public String toString() {
            return String.format("[%d,%d,%d]", x, y, z);
        }
    }

    static class PointXAY {
        public int x, y;
        public int[] a;

        protected PointXAY() { }
        public PointXAY(int x0, int y0, int[] a0) {
            x = x0;
            y = y0;
            a = a0;
        }

        @Override
        public boolean equals(Object o) {
            PointXAY other = (PointXAY) o;
            if ((x == other.x) && (y == other.y)) {
                if (other.a.length == a.length) {
                    for (int i = 0; i < a.length; ++i) {
                        if (other.a[i] != a[i]) {
                            return false;
                        }
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("[%d,%d,a=%s]", x, y,
                    Arrays.asList(a));
        }
    }

    @JsonPropertyOrder({ "x", "binary", "y" })
    static class PointXBinaryY {
        public int x, y;
        public byte[] binary;

        protected PointXBinaryY() { }
        public PointXBinaryY(int x0, int y0, byte[] b0) {
            x = x0;
            y = y0;
            binary = b0;
        }
    }

    private final AvroMapper MAPPER = getMapper();

    /*
    /**********************************************************************
    /* Success tests, simple
    /**********************************************************************
     */

    public void testAddField() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_XY_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_XYZ_JSON);
        final AvroSchema xlate = srcSchema.withReaderSchema(dstSchema);

        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(new PointXY(1, 2));
        PointXYZ result = MAPPER.readerFor(PointXYZ.class)
                .with(xlate)
                .readValue(avro);
        assertEquals(1, result.x);
        assertEquals(2, result.y);
        // expect default:
        assertEquals(99, result.z);

        // And same with a sequence
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SequenceWriter w = MAPPER.writer(srcSchema)
                .writeValues(out);
        w.write(new PointXY(2, -999));
        w.write(new PointXY(-4, 17));
        w.write(new PointXY(9, 0));
        w.close();
        MappingIterator<PointXYZ> it = MAPPER.readerFor(PointXYZ.class)
                .with(xlate)
                .readValues(out.toByteArray());
        assertTrue(it.hasNextValue());
        assertEquals(new PointXYZ(2, -999, 99), it.nextValue());
        assertTrue(it.hasNextValue());
        assertEquals(new PointXYZ(-4, 17, 99), it.nextValue());
        assertTrue(it.hasNextValue());
        assertEquals(new PointXYZ(9, 0, 99), it.nextValue());
        assertFalse(it.hasNextValue());
        it.close();
    }

    public void testRemoveField() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_XYZ_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_XY_JSON);
        final AvroSchema xlate = srcSchema.withReaderSchema(dstSchema);

        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(new PointXYZ(1, 2, 3));
        PointXY result = MAPPER.readerFor(PointXY.class)
                .with(xlate)
                .readValue(avro);
        assertEquals(1, result.x);
        assertEquals(2, result.y);

        // And same with a sequence
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SequenceWriter w = MAPPER.writer(srcSchema)
                .writeValues(out);
        w.write(new PointXYZ(2, -999, -4));
        w.write(new PointXYZ(-4, 17, 999));
        w.write(new PointXYZ(9, 0, 4));
        w.close();
        MappingIterator<PointXY> it = MAPPER.readerFor(PointXY.class)
                .with(xlate)
                .readValues(out.toByteArray());
        assertTrue(it.hasNextValue());
        assertEquals(new PointXY(2, -999), it.nextValue());
        assertTrue(it.hasNextValue());
        assertEquals(new PointXY(-4, 17), it.nextValue());
        assertTrue(it.hasNextValue());
        assertEquals(new PointXY(9, 0), it.nextValue());
        assertFalse(it.hasNextValue());
        it.close();
    }

    public void testRemoveArrayField() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_XAY_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_XY_JSON);
        final AvroSchema xlate = srcSchema.withReaderSchema(dstSchema);

        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(new PointXAY(1, 2,
                new int[] { 1, 2, 3 }));
        PointXY result = MAPPER.readerFor(PointXY.class)
                .with(xlate)
                .readValue(avro);
        assertEquals(1, result.x);
        assertEquals(2, result.y);

        // And same with a sequence
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SequenceWriter w = MAPPER.writer(srcSchema)
                .writeValues(out);
        PointXAY elem1 = new PointXAY(2, -999, new int[] { 1, 5 });
        w.write(elem1);
        PointXAY elem2 = new PointXAY(2, -999, new int[0]);
        w.write(elem2);
        PointXAY elem3 = new PointXAY(2, -999, new int[] { -63 });
        w.write(elem3);
        w.close();
        MappingIterator<PointXY> it = MAPPER.readerFor(PointXY.class)
                .with(xlate)
                .readValues(out.toByteArray());
        assertTrue(it.hasNextValue());
        PointXY result1 = it.nextValue();
        assertEquals(elem1.x, result1.x);
        assertEquals(elem1.y, result1.y);
        assertTrue(it.hasNextValue());
        PointXY result2 = it.nextValue();
        assertEquals(elem2.x, result2.x);
        assertEquals(elem2.y, result2.y);
        assertTrue(it.hasNextValue());
        PointXY result3 = it.nextValue();
        assertEquals(elem3.x, result3.x);
        assertEquals(elem3.y, result3.y);
        assertFalse(it.hasNextValue());
        it.close();
    }

    public void testRemoveBinaryField() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_XBY_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_XY_JSON);
        final AvroSchema xlate = srcSchema.withReaderSchema(dstSchema);

        byte[] binary = generateAsciiString(9000).getBytes("UTF-8");
        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(new PointXBinaryY(Integer.MIN_VALUE,
                Integer.MAX_VALUE, binary));

        PointXY result = MAPPER.readerFor(PointXY.class)
                .with(xlate)
                .readValue(LimitingInputStream.wrap(avro, 479));
        assertEquals(Integer.MIN_VALUE, result.x);
        assertEquals(Integer.MAX_VALUE, result.y);
    }

    /*
    /**********************************************************************
    /* Fail tests, basic
    /**********************************************************************
     */

    public void testFailNewFieldNoDefault() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_X_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_XY_JSON);
        try {
            srcSchema.withReaderSchema(dstSchema);
            fail("Should not pass");
        } catch (JsonMappingException e) {
            // 06-Feb-2017, tatu: Extremely lame error message by avro lib. Should consider
            //    rewriting to give some indication of issues...
            verifyException(e, "Incompatible writer/reader schemas");
            verifyException(e, "Data encoded using writer schema");
            verifyException(e, "will or may fail to decode using reader schema");
        }

        // However... should be possible with unsafe alternative
        AvroSchema risky = srcSchema.withUnsafeReaderSchema(dstSchema);
        assertNotNull(risky);
    }

    public void testFailNameChangeNoAlias() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_XYZ_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_XYZ_RENAMED_JSON);
        try {
            srcSchema.withReaderSchema(dstSchema);
            fail("Should not pass");
        } catch (JsonMappingException e) {
            // 16-Jun-2017, tatu: As is, names must match, so...
            verifyException(e, "Incompatible writer/reader schemas");
            verifyException(e, "root records have different names");
            verifyException(e, "\"EvolvingPoint\"");
            verifyException(e, "\"EvolvingPoint2\"");
        }
        // However... should be possible with unsafe alternative
        AvroSchema risky = srcSchema.withUnsafeReaderSchema(dstSchema);
        assertNotNull(risky);
    }
}
