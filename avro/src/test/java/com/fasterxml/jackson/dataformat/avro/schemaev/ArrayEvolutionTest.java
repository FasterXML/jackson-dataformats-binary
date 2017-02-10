package com.fasterxml.jackson.dataformat.avro.schemaev;

import com.fasterxml.jackson.databind.JsonMappingException;

import com.fasterxml.jackson.dataformat.avro.*;

public class ArrayEvolutionTest extends AvroTestBase
{
    // NOTE! Avro requires names of record types to match; this is why type names
    //   are identical despite differences in field composition...

    static String SCHEMA_X_ARRAY_JSON = aposToQuotes("{\n"+
            "'name': 'XArray',\n"+
            "'type': 'array',\n"+
            "'items': {\n"+
            " 'type':'record',\n"+
            " 'name':'EvolvingPoint',\n"+
            " 'fields':[\n"+
            "    { 'name':'x', 'type':'int' }\n"+
            " ]}}");

    static String SCHEMA_XY_ARRAY_JSON = aposToQuotes("{\n"+
            "'name': 'XYArray',\n"+
            "'type': 'array',\n"+
            "'items': {\n"+
            " 'type':'record',\n"+
            " 'name':'EvolvingPoint',\n"+
            " 'fields':[\n"+
            "    { 'name':'x', 'type':'int' },\n"+
            "    { 'name':'y', 'type':'int' }\n"+
            " ]}}");

    static String SCHEMA_XYZ_ARRAY_JSON = aposToQuotes("{\n"+
            "'name': 'XYZArray',\n"+
            "'type': 'array',\n"+
            "'items': {\n"+
            " 'type':'record',\n"+
            " 'name':'EvolvingPoint',\n"+
            " 'fields':[\n"+
            "    { 'name':'z', 'type':'int', 'default': 99 },\n"+
            "    { 'name':'y', 'type':'int' },\n"+
            "    { 'name':'x', 'type':'int' }\n"+
            " ]}}");

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
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_XY_ARRAY_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_XYZ_ARRAY_JSON);
        final AvroSchema xlate = srcSchema.withReaderSchema(dstSchema);

        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(new PointXY[] {
                new PointXY(1, 2), new PointXY(3, -9999), new PointXY(123456, 6)
        });
        PointXYZ[] result = MAPPER.readerFor(PointXYZ[].class)
                .with(xlate)
                .readValue(avro);
        assertEquals(3, result.length);
        assertEquals(result[0], new PointXYZ(1, 2, 99));
        assertEquals(result[1], new PointXYZ(3, -9999, 99));
        assertEquals(result[2], new PointXYZ(123456, 6, 99));
    }

    public void testRemoveField() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_XYZ_ARRAY_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_XY_ARRAY_JSON);
        final AvroSchema xlate = srcSchema.withReaderSchema(dstSchema);

        byte[] avro = MAPPER.writer(srcSchema).writeValueAsBytes(new PointXYZ[] {
                new PointXYZ(72, 555, -1), new PointXYZ(3, -300, 256), new PointXYZ(123456, 6, 1000)
        });
        PointXY[] result = MAPPER.readerFor(PointXY[].class)
                .with(xlate)
                .readValue(avro);
        assertEquals(3, result.length);
        assertEquals(result[0], new PointXY(72, 555));
        assertEquals(result[1], new PointXY(3, -300));
        assertEquals(result[2], new PointXY(123456, 6));
    }

    /*
    /**********************************************************************
    /* Fail tests, basic
    /**********************************************************************
     */

    public void testFailNewFieldNoDefault() throws Exception
    {
        final AvroSchema srcSchema = MAPPER.schemaFrom(SCHEMA_X_ARRAY_JSON);
        final AvroSchema dstSchema = MAPPER.schemaFrom(SCHEMA_XY_ARRAY_JSON);
        try {
            srcSchema.withReaderSchema(dstSchema);
            fail("Should not pass");
        } catch (JsonMappingException e) {
            // 06-Feb-2016, tatu: Extremely lame error message by avro lib. Should consider
            //    rewriting to give some indication of issues...
            verifyException(e, "Data encoded using writer schema");
            verifyException(e, "will or may fail to decode using reader schema");
        }
    }
}
