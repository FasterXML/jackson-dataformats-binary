package com.fasterxml.jackson.dataformat.avro.schema;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.AvroTestBase;

public class SchemaEvolutionTest extends AvroTestBase
{
    // NOTE! Avro requires named types to match; this is why type names
    //   are identical despite differences in field composition...

    static String SCHEMA_XY_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'EvolutionType',\n"+
            " 'fields':[\n"+
            "    { 'name':'x', 'type':'int' },\n"+
            "    { 'name':'y', 'type':'int' }\n"+
            " ]\n"+
            "}\n");

    static String SCHEMA_XYZ_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'EvolutionType',\n"+
            " 'fields':[\n"+
            "    { 'name':'z', 'type':'int', 'default': 99 },\n"+
            "    { 'name':'y', 'type':'int' },\n"+
            "    { 'name':'x', 'type':'int' }\n"+
            " ]\n"+
            "}\n");

    static String SCHEMA_X_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'EvolutionType',\n"+
            " 'fields':[\n"+
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
    }

    static class PointXYZ {
        public int x, y, z;

        protected PointXYZ() { }
        public PointXYZ(int x0, int y0, int z0) {
            x = x0;
            y = y0;
            z = z0;
        }
    }

    private final AvroMapper MAPPER = getMapper();

    /*
    /**********************************************************************
    /* Success tests, simple
    /**********************************************************************
     */

    /*
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
    }
    */

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
    }
}
