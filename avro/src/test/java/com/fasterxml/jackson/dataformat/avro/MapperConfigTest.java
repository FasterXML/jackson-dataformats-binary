package com.fasterxml.jackson.dataformat.avro;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.*;

public class MapperConfigTest extends AvroTestBase
{
    // Use shared mapper here to exercise it by some tests
    private final AvroMapper MAPPER = AvroMapper.shared();

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
        assertFalse(MAPPER.tokenStreamFactory().isEnabled(StreamWriteFeature.AUTO_CLOSE_CONTENT));

        assertFalse(MAPPER.tokenStreamFactory().canUseSchema(BOGUS_SCHEMA));
    }

    public void testParserDefaults() throws Exception
    {
        AvroParser p = (AvroParser) MAPPER.createParser(new byte[0]);
        assertTrue(p.isEnabled(AvroParser.Feature.AVRO_BUFFERING));
        p.close();

        AvroMapper mapper = AvroMapper.builder()
                .disable(AvroParser.Feature.AVRO_BUFFERING)
                .build();
        p = (AvroParser) mapper.createParser(new byte[0]);
        assertFalse(p.isEnabled(AvroParser.Feature.AVRO_BUFFERING));
        p.close();
    }

    public void testGeneratorDefaults() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final AvroSchema schema = getEmployeeSchema();
        AvroGenerator g = (AvroGenerator) MAPPER
                .writer()
                .with(schema)
                .createGenerator(bytes);
        assertTrue(g.isEnabled(AvroGenerator.Feature.AVRO_BUFFERING));
        g.close();

        AvroMapper mapper = AvroMapper.builder()
                .disable(AvroGenerator.Feature.AVRO_BUFFERING)
                .build();
        g = (AvroGenerator) mapper.writer()
                .with(schema)
                .createGenerator(bytes);
        assertFalse(g.isEnabled(AvroGenerator.Feature.AVRO_BUFFERING));
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

        // should be defaulting to sort-alphabetically, due to Avro format requiring ordering
        assertTrue(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));

        ObjectReader r = mapper.reader();
        assertTrue(r.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
    }
}
