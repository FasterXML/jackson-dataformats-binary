package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.databind.*;

public class MapperConfigTest extends AvroTestBase
{
    private final AvroFactory AVRO_F = new AvroFactory();

    private final FormatSchema BOGUS_SCHEMA = new FormatSchema() {
        @Override
        public String getSchemaType() {
            return "Test";
        }
    };

    /*
    /**********************************************************
    /* Defaults: streaming API
    /**********************************************************
     */

    public void testFactoryDefaults() throws Exception
    {
        assertTrue(AVRO_F.isEnabled(AvroParser.Feature.AVRO_BUFFERING));
        assertTrue(AVRO_F.isEnabled(AvroGenerator.Feature.AVRO_BUFFERING));
        assertFalse(AVRO_F.canUseSchema(BOGUS_SCHEMA));
    }

    public void testParserDefaults() throws Exception
    {
        AvroParser p = AVRO_F.createParser(new byte[0]);
        assertTrue(p.isEnabled(AvroParser.Feature.AVRO_BUFFERING));
        p.disable(AvroParser.Feature.AVRO_BUFFERING);
        assertFalse(p.isEnabled(AvroParser.Feature.AVRO_BUFFERING));
        try {
            p.setSchema(BOGUS_SCHEMA);
            fail("Should not pass!");
        } catch (IllegalArgumentException e) {
            ; // finel
        }
        p.close();
    }

    public void testGeneratorDefaults() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AvroGenerator g = AVRO_F.createGenerator(bytes);
        assertTrue(g.isEnabled(AvroGenerator.Feature.AVRO_BUFFERING));
        g.disable(AvroGenerator.Feature.AVRO_BUFFERING);
        assertFalse(g.isEnabled(AvroGenerator.Feature.AVRO_BUFFERING));

        try {
            g.setSchema(BOGUS_SCHEMA);
            fail("Should not pass!");
        } catch (IllegalArgumentException e) {
            ; // finel
        }
        g.close();

        
    }

    /*
    /**********************************************************
    /* Defaults: Mapper, related
    /**********************************************************
     */
    
    // Test to verify that data format affects default state of order-props-alphabetically
    public void testDefaultSettingsWithObjectMapper()
    {
        ObjectMapper mapper = new ObjectMapper(AVRO_F);
        _testAvroMapperDefaults(mapper);

        // and even with default mapper, may become so, if configred with AvroFactory
        ObjectMapper vanilla = new ObjectMapper();
        assertFalse(vanilla.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        ObjectReader r = vanilla.reader();
        assertFalse(r.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        r = r.with(AVRO_F);

        assertTrue(r.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
    }

    public void testDefaultSettingsWithAvroMapper()
    {
        AvroMapper mapper = new AvroMapper();
        assertNotNull(mapper.version());

        _testAvroMapperDefaults(mapper);
        _testAvroMapperDefaults(mapper.copy());
    }
    
    protected void _testAvroMapperDefaults(ObjectMapper mapper)
    {
        // should be defaulting to sort-alphabetically, due to Avro format requiring ordering
        assertTrue(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));

        ObjectReader r = mapper.reader();
        assertTrue(r.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
    }
}
