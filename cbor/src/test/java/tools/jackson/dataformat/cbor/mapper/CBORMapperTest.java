package tools.jackson.dataformat.cbor.mapper;

import tools.jackson.dataformat.cbor.CBORTestBase;
import tools.jackson.dataformat.cbor.CBORWriteFeature;
import tools.jackson.dataformat.cbor.databind.CBORMapper;

import org.junit.Assert;

public class CBORMapperTest extends CBORTestBase
{
    /*
    /**********************************************************
    /* Tests for [dataformats-binary#301]
    /**********************************************************
     */

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
        Assert.assertArrayEquals(minimalValues, mapperWithMinimal.readValue(encodedMinimal, Object[].class));
        Assert.assertArrayEquals(values, mapperWithMinimal.readValue(encodedNotMinimal, Object[].class));
        Assert.assertArrayEquals(minimalValues, mapperFull.readValue(encodedMinimal, Object[].class));
        Assert.assertArrayEquals(values, mapperFull.readValue(encodedNotMinimal, Object[].class));
    }
}
