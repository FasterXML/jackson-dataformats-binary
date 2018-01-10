package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;

public class MapperConfigTest extends AvroTestBase
{
    private final AvroMapper MAPPER = getMapper();

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
        assertTrue(MAPPER.tokenStreamFactory().isEnabled(AvroParser.Feature.AVRO_BUFFERING));

        assertTrue(MAPPER.tokenStreamFactory().isEnabled(AvroGenerator.Feature.AVRO_BUFFERING));
        assertFalse(MAPPER.tokenStreamFactory().isEnabled(JsonGenerator.Feature.AUTO_CLOSE_CONTENT));

        assertFalse(MAPPER.tokenStreamFactory().canUseSchema(BOGUS_SCHEMA));
    }

    public void testParserDefaults() throws Exception
    {
        AvroParser p = (AvroParser) MAPPER.createParser(new byte[0]);
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
        AvroGenerator g = (AvroGenerator) MAPPER.createGenerator(bytes);
        assertTrue(g.isEnabled(AvroGenerator.Feature.AVRO_BUFFERING));
        g.disable(AvroGenerator.Feature.AVRO_BUFFERING);
        assertFalse(g.isEnabled(AvroGenerator.Feature.AVRO_BUFFERING));
        
        try {
            g.setSchema(BOGUS_SCHEMA);
            fail("Should not pass!");
        } catch (IllegalArgumentException e) {
            verifyException(e, "Can not use FormatSchema of type ");
        }
        g.close();

        
    }

    /*
    /**********************************************************
    /* Defaults: Mapper, related
    /**********************************************************
     */

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
