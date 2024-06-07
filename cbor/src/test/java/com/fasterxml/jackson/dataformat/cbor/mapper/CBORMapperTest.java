package com.fasterxml.jackson.dataformat.cbor.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;

import org.junit.Assert;

public class CBORMapperTest extends CBORTestBase
{
    // For [dataformats-binary#301]
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
                .enable(CBORGenerator.Feature.WRITE_MINIMAL_INTS)
                .enable(CBORGenerator.Feature.WRITE_MINIMAL_DOUBLES)
                .build();
        byte[] encodedMinimal = mapperWithMinimal.writeValueAsBytes(values);
        assertEquals(21, encodedMinimal.length);

        CBORMapper mapperFull = CBORMapper.builder()
                .disable(CBORGenerator.Feature.WRITE_MINIMAL_INTS)
                .disable(CBORGenerator.Feature.WRITE_MINIMAL_DOUBLES)
                .build();
        byte[] encodedNotMinimal = mapperFull.writeValueAsBytes(values);
        assertEquals(29, encodedNotMinimal.length);

        // And then verify we can read it back, either way
        Assert.assertArrayEquals(minimalValues, mapperWithMinimal.readValue(encodedMinimal, Object[].class));
        Assert.assertArrayEquals(values, mapperWithMinimal.readValue(encodedNotMinimal, Object[].class));
        Assert.assertArrayEquals(minimalValues, mapperFull.readValue(encodedMinimal, Object[].class));
        Assert.assertArrayEquals(values, mapperFull.readValue(encodedNotMinimal, Object[].class));
    }

    // [databind#3212]
    public void testMapperCopy() throws Exception
    {
        CBORMapper src = cborMapper();
        assertNotSame(src, src.copy());

        CBORFactory streamingF = new CBORFactory();
        ObjectMapper m2 = src.copyWith(streamingF);
        assertNotSame(src, m2);
        assertSame(streamingF, m2.tokenStreamFactory());
    }
}
