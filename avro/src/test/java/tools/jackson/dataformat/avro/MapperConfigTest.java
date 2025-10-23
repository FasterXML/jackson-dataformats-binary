package tools.jackson.dataformat.avro;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.core.FormatSchema;
import tools.jackson.core.StreamReadCapability;
import tools.jackson.core.StreamWriteFeature;

import tools.jackson.databind.*;

import static org.junit.jupiter.api.Assertions.*;

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
    /**********************************************************************
    /* Defaults: streaming API
    /**********************************************************************
     */

    @Test
    public void testFactoryDefaults() throws Exception
    {
        assertTrue(MAPPER.tokenStreamFactory().isEnabled(AvroReadFeature.AVRO_BUFFERING));

        assertTrue(MAPPER.tokenStreamFactory().isEnabled(AvroWriteFeature.AVRO_BUFFERING));
        assertFalse(MAPPER.tokenStreamFactory().isEnabled(StreamWriteFeature.AUTO_CLOSE_CONTENT));

        assertFalse(MAPPER.tokenStreamFactory().canUseSchema(BOGUS_SCHEMA));
    }

    @Test
    public void testParserDefaults() throws Exception
    {
        try (AvroParser p = (AvroParser) MAPPER.createParser(new byte[0])) {
            assertTrue(p.isEnabled(AvroReadFeature.AVRO_BUFFERING));
        }

        AvroMapper mapper = AvroMapper.builder()
                .disable(AvroReadFeature.AVRO_BUFFERING)
                .build();
        try (AvroParser p = (AvroParser) mapper.createParser(new byte[0])) {
            assertFalse(p.isEnabled(AvroReadFeature.AVRO_BUFFERING));
    
            // 15-Jan-2021, tatu: 2.14 added this setting, not enabled in
            //    default set
            assertTrue(p.streamReadCapabilities().isEnabled(StreamReadCapability.EXACT_FLOATS));
        }

        // [dataformats-binary#619]
        assertTrue(MAPPER.isEnabled(AvroReadFeature.AVRO_BUFFERING));
}

    @Test
    public void testGeneratorDefaults() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final AvroSchema schema = getEmployeeSchema();
        AvroGenerator g = (AvroGenerator) MAPPER
                .writer()
                .with(schema)
                .createGenerator(bytes);
        assertTrue(g.isEnabled(AvroWriteFeature.AVRO_BUFFERING));
        g.close();

        AvroMapper mapper = AvroMapper.builder()
                .disable(AvroWriteFeature.AVRO_BUFFERING)
                .build();
        g = (AvroGenerator) mapper.writer()
                .with(schema)
                .createGenerator(bytes);
        assertFalse(g.isEnabled(AvroWriteFeature.AVRO_BUFFERING));
        g.close();

        // [dataformats-binary#619]
        assertFalse(MAPPER.isEnabled(AvroWriteFeature.AVRO_FILE_OUTPUT));
    }

    /*
    /**********************************************************************
    /* Defaults: Mapper, related
    /**********************************************************************
     */

    @Test
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
