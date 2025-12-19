package tools.jackson.dataformat.cbor.mapper;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.cbor.CBORMapper;
import tools.jackson.dataformat.cbor.CBORReadFeature;
import tools.jackson.dataformat.cbor.CBORTestBase;
import tools.jackson.dataformat.cbor.CBORWriteFeature;

import java.math.BigInteger;

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

    // [dataformats-binary#431]
    @Test
    public void testSimpleNegativeBigInteger() throws Exception {
        byte[] encodedNegativeOne = {
                (byte) 0xC3,  // tag 3 (negative big integer)
                (byte) 0x41,  // byte string, length 1
                (byte) 0x00   // value 0 (become -1 after decoding)
        };

        // Test correct decoding
        CBORMapper mapper1 = CBORMapper.builder().build();
        assertEquals(BigInteger.valueOf(-1),
                mapper1.readValue(encodedNegativeOne, BigInteger.class));

        // Test incorrect decoding for compatibility
        CBORMapper mapper2 = CBORMapper.builder()
                .disable(CBORReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING)
                .build();
        assertEquals(BigInteger.ZERO,
                mapper2.readValue(encodedNegativeOne, BigInteger.class));
    }


    // [dataformats-binary#431]
    @Test
    public void testNegativeBigInteger() throws Exception {
        // correct encoding: https://cbor.me/?bytes=c35100ffffffffffffffffffffffffffffffff
        byte[] encodedNegative = {
                (byte) 0xC3,
                (byte) 0x51,
                (byte) 0x00, // leading zero
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF
        };

        // Test correct decoding
        CBORMapper mapper1 = CBORMapper.builder().build();
        assertEquals(new BigInteger("-340282366920938463463374607431768211456"),
                mapper1.readValue(encodedNegative, BigInteger.class));


        // Test incorrect decoding for compatibility
        CBORMapper mapper2 = CBORMapper.builder()
                .disable(CBORReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING)
                .build();
        assertEquals(new BigInteger("-340282366920938463463374607431768211455"),
                mapper2.readValue(encodedNegative, BigInteger.class));
    }

    // [dataformats-binary#431]
    @Test
    public void testNegativeBigIntegerWithoutLeadingZero() throws Exception {
        // correct encoding: https://cbor.me/?bytes=c350ffffffffffffffffffffffffffffffff
        byte[] encodedNegative = {
                (byte) 0xC3,
                (byte) 0x50,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF
        };

        // Test correct decoding
        CBORMapper mapper1 = CBORMapper.builder().build();
        assertEquals(new BigInteger("-340282366920938463463374607431768211456"),
                mapper1.readValue(encodedNegative, BigInteger.class));


        // Test incorrect decoding for compatibility
        CBORMapper mapper2 = CBORMapper.builder()
                .disable(CBORReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING)
                .build();
        assertEquals(BigInteger.ONE,
                mapper2.readValue(encodedNegative, BigInteger.class));
    }

    // [dataformats-binary#619]
    @Test
    void testFormatFeatureDefaults() {
        CBORMapper mapper = CBORMapper.shared();
        assertTrue(mapper.isEnabled(CBORReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING));
        assertTrue(mapper.isEnabled(CBORWriteFeature.WRITE_MINIMAL_INTS));
    }
}
