package tools.jackson.dataformat.cbor.mapper;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.cbor.CBORTestBase;
import tools.jackson.dataformat.cbor.CBORWriteFeature;
import tools.jackson.dataformat.cbor.databind.CBORMapper;

import static org.junit.jupiter.api.Assertions.*;

public class CBORMapperTest extends CBORTestBase
{
    /*
    /**********************************************************************
    /* Tests for [dataformats-binary#301]
    /**********************************************************************
     */

    @Test
    public void testStreamingFeaturesViaMapper() throws Exception
    {
        final int SMALL_INT = 3;
        final int BIG_INT = 0x7FFFFFFF;
        final double LOW_RPECISION_DOUBLE = 1.5;
        final double HIGH_RPECISION_DOUBLE = 0.123456789;
        Object[] values = {SMALL_INT, BIG_INT, LOW_RPECISION_DOUBLE, HIGH_RPECISION_DOUBLE};
        Object[] minimalValues = {
                SMALL_INT, BIG_INT, (float)LOW_RPECISION_DOUBLE, HIGH_RPECISION_DOUBLE};
        CBORMapper mapperWithMinimal = CBORMapper.builder()
                .enable(CBORWriteFeature.WRITE_MINIMAL_INTS)
                .enable(CBORWriteFeature.WRITE_MINIMAL_DOUBLES)
                .build();
        byte[] encodedMinimal = mapperWithMinimal.writeValueAsBytes(values);
        assertEquals(21, encodedMinimal.length);

        CBORMapper mapperFull = CBORMapper.builder()
                .disable(CBORWriteFeature.WRITE_MINIMAL_INTS)
                .disable(CBORWriteFeature.WRITE_MINIMAL_DOUBLES)
                .build();
        byte[] encodedNotMinimal = mapperFull.writeValueAsBytes(values);
        assertEquals(29, encodedNotMinimal.length);

        // And then verify we can read it back, either way
        assertArrayEquals(minimalValues, mapperWithMinimal.readValue(encodedMinimal, Object[].class));
        assertArrayEquals(values, mapperWithMinimal.readValue(encodedNotMinimal, Object[].class));
        assertArrayEquals(minimalValues, mapperFull.readValue(encodedMinimal, Object[].class));
        assertArrayEquals(values, mapperFull.readValue(encodedNotMinimal, Object[].class));
    }
}
