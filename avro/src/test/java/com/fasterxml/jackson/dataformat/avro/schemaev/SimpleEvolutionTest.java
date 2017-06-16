package com.fasterxml.jackson.dataformat.avro.schemaev;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.SequenceWriter;

import com.fasterxml.jackson.dataformat.avro.*;

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
            // 06-Feb-2016, tatu: Extremely lame error message by avro lib. Should consider
            //    rewriting to give some indication of issues...
            verifyException(e, "Data encoded using writer schema");
            verifyException(e, "will or may fail to decode using reader schema");
        }

        // However... should be possible with unsafe alternative
        AvroSchema risky = srcSchema.withUnsafeReaderSchema(dstSchema);
        assertNotNull(risky);
    }
}
