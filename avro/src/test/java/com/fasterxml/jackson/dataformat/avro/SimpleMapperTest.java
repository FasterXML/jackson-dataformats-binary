package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.databind.*;

public class SimpleMapperTest extends AvroTestBase
{
    // Test to verify that data format affects default state of order-props-alphabetically
    public void testDefaultSettingsWithObjectMapper()
    {
        AvroFactory av = new AvroFactory();
        ObjectMapper mapper = new ObjectMapper(av);
        _testAvroMapperDefaults(mapper);

        // and even with default mapper, may become so, if configred with AvroFactory
        ObjectMapper vanilla = new ObjectMapper();
        assertFalse(vanilla.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        ObjectReader r = vanilla.reader();
        assertFalse(r.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        r = r.with(av);
        assertTrue(r.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
    }

    public void testDefaultSettingsWithAvroMapper()
    {
        AvroMapper avroMapper = new AvroMapper();
        _testAvroMapperDefaults(avroMapper);

        _testAvroMapperDefaults(avroMapper.copy());
    }
    
    protected void _testAvroMapperDefaults(ObjectMapper mapper)
    {
        // should be defaulting to sort-alphabetically, due to Avro format requiring ordering
        assertTrue(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));

        ObjectReader r = mapper.reader();
        assertTrue(r.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
    }
}
