package tools.jackson.dataformat.cbor.mapper;

import tools.jackson.dataformat.cbor.CBORGenerator;
import tools.jackson.dataformat.cbor.CBORTestBase;
import tools.jackson.dataformat.cbor.databind.CBORMapper;

public class CBORMapperTest extends CBORTestBase
{
    /*
    /**********************************************************
    /* Tests for [dataformats-binary#301]
    /**********************************************************
     */

    public void testStreamingFeaturesViaMapper() throws Exception
    {
        final Integer SMALL_INT = Integer.valueOf(3);
        CBORMapper mapperWithMinimalInts = CBORMapper.builder()
                .enable(CBORGenerator.Feature.WRITE_MINIMAL_INTS)
                .build();
        byte[] encodedMinimal = mapperWithMinimalInts.writeValueAsBytes(SMALL_INT);
        assertEquals(1, encodedMinimal.length);

        CBORMapper mapperFullInts = CBORMapper.builder()
                .disable(CBORGenerator.Feature.WRITE_MINIMAL_INTS)
                .build();
        byte[] encodedNotMinimal = mapperFullInts.writeValueAsBytes(SMALL_INT);
        assertEquals(5, encodedNotMinimal.length);

        // And then verify we can read it back, either way
        assertEquals(SMALL_INT, mapperWithMinimalInts.readValue(encodedMinimal, Object.class));
        assertEquals(SMALL_INT, mapperWithMinimalInts.readValue(encodedNotMinimal, Object.class));
        assertEquals(SMALL_INT, mapperFullInts.readValue(encodedMinimal, Object.class));
        assertEquals(SMALL_INT, mapperFullInts.readValue(encodedNotMinimal, Object.class));
    }
}
