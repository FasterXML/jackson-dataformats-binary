package com.fasterxml.jackson.dataformat.smile.async;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#830]: end-of-input in the middle of a token must be
// reported as an error, not silently treated as end of content
public class AsyncTruncatedInputTest extends AsyncTestBase
{
    private final SmileFactory F_RAW = new SmileFactory();
    {
        F_RAW.disable(SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT);
    }

    private final SmileFactory F_7BIT = new SmileFactory();
    {
        F_7BIT.enable(SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT);
    }

    private final static String LONG_TEXT = "abcdefghijklmnopqrstuvwxyz0123456789"
            + "abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz0123456789";

    /*
    /**********************************************************************
    /* Test methods, truncated values
    /**********************************************************************
     */

    @Test
    public void testTruncatedRawBinary() throws Exception
    {
        _verifyTruncated(F_RAW, g -> g.writeBinary(new byte[300]), "Binary value (raw)");
    }

    @Test
    public void testTruncated7BitBinary() throws Exception
    {
        _verifyTruncated(F_7BIT, g -> g.writeBinary(new byte[300]), "Binary value (7-bit)");
    }

    @Test
    public void testTruncatedLongString() throws Exception
    {
        _verifyTruncated(F_RAW, g -> g.writeString(LONG_TEXT), "String value");
    }

    @Test
    public void testTruncatedShortString() throws Exception
    {
        _verifyTruncated(F_RAW, g -> g.writeString("abcdef"), "String value");
    }

    @Test
    public void testTruncatedInt() throws Exception
    {
        _verifyTruncated(F_RAW, g -> g.writeNumber(Integer.MAX_VALUE), "Number value");
    }

    @Test
    public void testTruncatedDouble() throws Exception
    {
        _verifyTruncated(F_RAW, g -> g.writeNumber(0.25), "Number value");
    }

    @Test
    public void testTruncatedBigInteger() throws Exception
    {
        _verifyTruncated(F_RAW, g -> g.writeNumber(new BigInteger("123456789012345678901234567890")),
                "Number value");
    }

    @Test
    public void testTruncatedBigDecimal() throws Exception
    {
        _verifyTruncated(F_RAW, g -> g.writeNumber(new BigDecimal("1234567890123456789.0123456789")),
                "Number value");
    }

    @Test
    public void testTruncatedFieldName() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = F_RAW.createGenerator(bytes)) {
            g.writeStartObject();
            g.writeFieldName(LONG_TEXT);
            g.writeNumber(1);
            g.writeEndObject();
        }
        // remove END_OBJECT, value and part of name (including its end marker)
        byte[] doc = bytes.toByteArray();
        _verifyEOF(F_RAW, Arrays.copyOf(doc, doc.length - 10), "Field name");
    }

    @Test
    public void testTruncatedHeader() throws Exception
    {
        _verifyEOF(F_RAW, new byte[] { ':', ')' }, "Smile header");
    }

    /*
    /**********************************************************************
    /* Test methods, valid end-of-input
    /**********************************************************************
     */

    @Test
    public void testEmptyInput() throws Exception
    {
        for (int chunk : new int[] { 1, 99 }) {
            AsyncReaderWrapper r = asyncForBytes(F_RAW, chunk, new byte[0], 0);
            try {
                assertNull(r.nextToken());
            } finally {
                r.close();
            }
        }
    }

    @Test
    public void testCompleteRootValue() throws Exception
    {
        byte[] doc = _doc(F_RAW, g -> g.writeString(LONG_TEXT));
        for (int chunk : new int[] { 1, 3, doc.length }) {
            AsyncReaderWrapper r = asyncForBytes(F_RAW, chunk, doc, 0);
            try {
                assertToken(JsonToken.VALUE_STRING, r.nextToken());
                assertEquals(LONG_TEXT, r.currentText());
                assertNull(r.nextToken());
            } finally {
                r.close();
            }
        }
    }

    // Value that failed validation is skipped; skipping all of it before
    // end-of-input is fine, but end-of-input while skipping is not
    @Test
    public void testEndOfInputAfterSkippedValue() throws Exception
    {
        final SmileFactory f = SmileFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNumberLength(10).build())
                .build();
        final byte[] doc = _doc(F_RAW,
                g -> g.writeNumber(new BigInteger("1234567890123456789012345678901234567890")));

        for (int chunk : new int[] { 1, 3, doc.length }) {
            AsyncReaderWrapper r = asyncForBytes(f, chunk, doc, 0);
            try {
                assertThrows(StreamConstraintsException.class, () -> r.nextToken());
                assertNull(r.nextToken());
            } finally {
                r.close();
            }

            byte[] truncated = Arrays.copyOf(doc, doc.length - 3);
            AsyncReaderWrapper r2 = asyncForBytes(f, chunk, truncated, 0);
            try {
                assertThrows(StreamConstraintsException.class, () -> r2.nextToken());
                JsonEOFException e = assertThrows(JsonEOFException.class, () -> r2.nextToken());
                verifyException(e, "Unexpected end-of-input in Number value (being skipped)");
            } finally {
                r2.close();
            }
        }
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    interface ValueWriter {
        void write(JsonGenerator g) throws Exception;
    }

    // Verifies failure for truncated value both as root value and within Array
    private void _verifyTruncated(SmileFactory f, ValueWriter w, String expDesc)
        throws Exception
    {
        byte[] doc = _doc(f, w);
        _verifyEOF(f, Arrays.copyOf(doc, doc.length - 1), expDesc);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = f.createGenerator(bytes)) {
            g.writeStartArray();
            w.write(g);
            g.writeEndArray();
        }
        // remove END_ARRAY and last byte of value
        doc = bytes.toByteArray();
        _verifyEOF(f, Arrays.copyOf(doc, doc.length - 2), expDesc);
    }

    private void _verifyEOF(SmileFactory f, byte[] doc, String expDesc) throws Exception
    {
        for (int chunk : new int[] { 1, 3, doc.length }) {
            AsyncReaderWrapper r = asyncForBytes(f, chunk, doc, 0);
            try {
                JsonToken t;
                while ((t = r.nextToken()) != null) {
                    // START_ARRAY, START_OBJECT, FIELD_NAME fine; values not
                    assertTrue(t.isStructStart() || (t == JsonToken.FIELD_NAME),
                            "Unexpected token "+t+" (chunk size "+chunk+")");
                }
                fail("Should not pass (chunk size "+chunk+")");
            } catch (JsonEOFException e) {
                verifyException(e, "Unexpected end-of-input in "+expDesc);
            } finally {
                r.close();
            }
        }
    }

    private byte[] _doc(SmileFactory f, ValueWriter w) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = f.createGenerator(bytes)) {
            w.write(g);
        }
        return bytes.toByteArray();
    }
}
