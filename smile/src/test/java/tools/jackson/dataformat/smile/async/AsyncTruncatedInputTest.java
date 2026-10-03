package tools.jackson.dataformat.smile.async;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.async.ByteArrayFeeder;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.exc.UnexpectedEndOfInputException;
import tools.jackson.dataformat.smile.SmileConstants;
import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.SmileMapper;
import tools.jackson.dataformat.smile.SmileWriteFeature;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#830]: end-of-input in the middle of a token must be
// reported as an error, not silently treated as end of content
public class AsyncTruncatedInputTest extends AsyncTestBase
{
    private final SmileMapper F_RAW = new SmileMapper(SmileFactory.builder()
            .disable(SmileWriteFeature.ENCODE_BINARY_AS_7BIT)
            .build());

    private final SmileMapper F_7BIT = new SmileMapper(SmileFactory.builder()
            .enable(SmileWriteFeature.ENCODE_BINARY_AS_7BIT)
            .build());

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
            g.writeName(LONG_TEXT);
            g.writeNumber(1);
            g.writeEndObject();
        }
        // cut within name: before its last byte (and end marker)
        byte[] doc = bytes.toByteArray();
        int nameEnd = _lastIndexOf(doc, (byte) SmileConstants.BYTE_MARKER_END_OF_STRING);
        assertTrue(nameEnd > 0);
        _verifyEOF(F_RAW, Arrays.copyOf(doc, nameEnd - 1), "Property name");
    }

    @Test
    public void testTruncatedHeader() throws Exception
    {
        _verifyEOF(F_RAW, new byte[] { ':', ')' }, "Smile header");
    }

    @Test
    public void testTruncatedUnicodeStrings() throws Exception
    {
        // short Unicode
        _verifyTruncated(F_RAW, g -> g.writeString("\u00C5\u00E4\u00F6 xyz"), "String value");
        // long Unicode, cut within last (2-byte) character
        final String longText = LONG_TEXT + "\u00E9";
        byte[] doc = _doc(F_RAW, g -> g.writeString(longText));
        _verifyEOF(F_RAW, Arrays.copyOf(doc, doc.length - 2), "String value");
    }

    @Test
    public void testTruncatedLongAndFloat() throws Exception
    {
        _verifyTruncated(F_RAW, g -> g.writeNumber(Long.MAX_VALUE), "Number value");
        _verifyTruncated(F_RAW, g -> g.writeNumber(0.25f), "Number value");
    }

    // Truncation within length (and scale) prefixes, not just content
    @Test
    public void testTruncatedLengthPrefixes() throws Exception
    {
        final int HEADER_LEN = 4;
        // BigInteger, 2-byte length: cut after first length byte
        byte[] doc = _doc(F_RAW, g -> g.writeNumber(BigInteger.ONE.shiftLeft(800)));
        _verifyEOF(F_RAW, Arrays.copyOf(doc, HEADER_LEN + 2), "Number value");
        // BigDecimal: cut after type byte (in scale), and after 1-byte scale (in length)
        doc = _doc(F_RAW, g -> g.writeNumber(new BigDecimal(BigInteger.ONE.shiftLeft(800), 2)));
        _verifyEOF(F_RAW, Arrays.copyOf(doc, HEADER_LEN + 1), "Number value");
        _verifyEOF(F_RAW, Arrays.copyOf(doc, HEADER_LEN + 2), "Number value");
        _verifyEOF(F_RAW, Arrays.copyOf(doc, HEADER_LEN + 3), "Number value");
        // Binary, 2-byte length: cut after type byte and after first length byte
        doc = _doc(F_RAW, g -> g.writeBinary(new byte[300]));
        _verifyEOF(F_RAW, Arrays.copyOf(doc, HEADER_LEN + 1), "Binary value (raw)");
        _verifyEOF(F_RAW, Arrays.copyOf(doc, HEADER_LEN + 2), "Binary value (raw)");
        doc = _doc(F_7BIT, g -> g.writeBinary(new byte[300]));
        _verifyEOF(F_7BIT, Arrays.copyOf(doc, HEADER_LEN + 1), "Binary value (7-bit)");
        _verifyEOF(F_7BIT, Arrays.copyOf(doc, HEADER_LEN + 2), "Binary value (7-bit)");
    }

    // Header of a following document (inline header), truncated
    @Test
    public void testTruncatedInlineHeader() throws Exception
    {
        byte[] doc = concat(_doc(F_RAW, g -> g.writeNumber(1)), new byte[] { ':', ')' });
        _verifyEOFAfterValues(F_RAW, doc, "Smile header", null);
    }

    // 2-byte shared String value reference, truncated
    @Test
    public void testTruncatedSharedStringReference() throws Exception
    {
        final SmileMapper f = new SmileMapper(SmileFactory.builder()
                .enable(SmileWriteFeature.CHECK_SHARED_STRING_VALUES)
                .build());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = f.createGenerator(bytes)) {
            g.writeStartArray();
            // need more than 31 to get 2-byte references
            for (int i = 0; i < 40; ++i) {
                g.writeString("value"+i);
            }
            g.writeString("value35");
            g.writeEndArray();
        }
        // remove END_ARRAY and second byte of reference
        byte[] doc = bytes.toByteArray();
        _verifyEOFAfterValues(f, Arrays.copyOf(doc, doc.length - 2), "String value",
                JsonToken.VALUE_STRING);
    }

    // 2-byte shared name reference, truncated
    @Test
    public void testTruncatedSharedNameReference() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = F_RAW.createGenerator(bytes)) {
            g.writeStartArray();
            // need more than 63 to get 2-byte references
            for (int i = 0; i < 70; ++i) {
                g.writeStartObject();
                g.writeNumberProperty("name"+i, 1);
                g.writeEndObject();
            }
            g.writeStartObject();
            g.writeNumberProperty("name65", 1);
            g.writeEndObject();
            g.writeEndArray();
        }
        // remove END_ARRAY, END_OBJECT, value and second byte of reference
        byte[] doc = bytes.toByteArray();
        _verifyEOFAfterValues(F_RAW, Arrays.copyOf(doc, doc.length - 4), "Property name",
                JsonToken.START_OBJECT);
    }

    // Parser should be closed after failure, as with regular end-of-input
    @Test
    public void testStateAfterFailure() throws Exception
    {
        byte[] doc = _doc(F_RAW, g -> g.writeString(LONG_TEXT));
        doc = Arrays.copyOf(doc, doc.length - 1);
        try (JsonParser p = F_RAW.createNonBlockingByteArrayParser()) {
            ByteArrayFeeder feeder = (ByteArrayFeeder) p.nonBlockingInputFeeder();
            feeder.feedInput(doc, 0, doc.length);
            assertToken(JsonToken.NOT_AVAILABLE, p.nextToken());
            feeder.endOfInput();
            UnexpectedEndOfInputException e = assertThrows(UnexpectedEndOfInputException.class, () -> p.nextToken());
            verifyException(e, "Unexpected end-of-input in String value");
            assertEquals(JsonToken.VALUE_STRING, e.getTokenBeingDecoded());

            assertTrue(p.isClosed());
            assertFalse(feeder.needMoreInput());
            assertNull(p.currentToken());
            assertNull(p.nextToken());
        }
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
        final SmileMapper f = new SmileMapper(SmileFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNumberLength(10).build())
                .build());
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
                UnexpectedEndOfInputException e = assertThrows(UnexpectedEndOfInputException.class, () -> r2.nextToken());
                verifyException(e, "Unexpected end-of-input in Number value (being skipped)");
                assertEquals(JsonToken.VALUE_NUMBER_INT, e.getTokenBeingDecoded());
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
    private void _verifyTruncated(SmileMapper f, ValueWriter w, String expDesc)
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

    private void _verifyEOF(SmileMapper f, byte[] doc, String expDesc) throws Exception
    {
        for (int chunk : new int[] { 1, 3, doc.length }) {
            AsyncReaderWrapper r = asyncForBytes(f, chunk, doc, 0);
            try {
                JsonToken t;
                while ((t = r.nextToken()) != null) {
                    // START_ARRAY, START_OBJECT, PROPERTY_NAME fine; values not
                    assertTrue(t.isStructStart() || (t == JsonToken.PROPERTY_NAME),
                            "Unexpected token "+t+" (chunk size "+chunk+")");
                }
                fail("Should not pass (chunk size "+chunk+")");
            } catch (UnexpectedEndOfInputException e) {
                verifyException(e, "Unexpected end-of-input in "+expDesc);
            } finally {
                r.close();
            }
        }
    }

    // Verifies failure after some tokens; last of them of given type (if any)
    private void _verifyEOFAfterValues(SmileMapper f, byte[] doc, String expDesc,
            JsonToken expLastToken)
        throws Exception
    {
        for (int chunk : new int[] { 1, 3, doc.length }) {
            AsyncReaderWrapper r = asyncForBytes(f, chunk, doc, 0);
            JsonToken last = null;
            try {
                JsonToken t;
                while ((t = r.nextToken()) != null) {
                    last = t;
                }
                fail("Should not pass (chunk size "+chunk+")");
            } catch (UnexpectedEndOfInputException e) {
                verifyException(e, "Unexpected end-of-input in "+expDesc);
                if (expLastToken != null) {
                    assertEquals(expLastToken, last, "chunk size "+chunk);
                }
            } finally {
                r.close();
            }
        }
    }

    private int _lastIndexOf(byte[] data, byte b)
    {
        for (int i = data.length; --i >= 0; ) {
            if (data[i] == b) {
                return i;
            }
        }
        return -1;
    }

    private byte[] _doc(SmileMapper f, ValueWriter w) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = f.createGenerator(bytes)) {
            w.write(g);
        }
        return bytes.toByteArray();
    }
}
