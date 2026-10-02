package tools.jackson.dataformat.smile.constraints;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.async.ByteArrayFeeder;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.exc.StreamReadException;

import tools.jackson.dataformat.smile.SmileConstants;
import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.SmileMapper;
import tools.jackson.dataformat.smile.async.AsyncReaderWrapper;
import tools.jackson.dataformat.smile.async.AsyncTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// Declared length of BigInteger/BigDecimal values should be validated against
// `maxNumberLength` before content is read (and buffered), not after
public class LongBigNumberSmileReadTest extends AsyncTestBase
{
    private final static byte[] HEADER = new byte[] {
            SmileConstants.HEADER_BYTE_1, SmileConstants.HEADER_BYTE_2,
            SmileConstants.HEADER_BYTE_3, 0
    };

    // 8_000_000 as unsigned VInt: way above default `maxNumberLength` (1000)
    private final static byte[] LONG_LENGTH = new byte[] { 0x07, 0x50, 0x48, (byte) 0x80 };

    // Just a bit of content: nowhere near declared length
    private final static byte[] SHORT_CONTENT = new byte[20];

    private final SmileMapper F = new SmileMapper();

    @Test
    public void testLongBigInteger() throws Exception
    {
        _verifyEarlyFailure(concat(HEADER,
                new byte[] { SmileConstants.TOKEN_PREFIX_INTEGER + 2 },
                LONG_LENGTH, SHORT_CONTENT), JsonToken.VALUE_NUMBER_INT);
    }

    @Test
    public void testLongBigDecimal() throws Exception
    {
        _verifyEarlyFailure(concat(HEADER,
                new byte[] { SmileConstants.TOKEN_PREFIX_FP + 2, (byte) 0x80 }, // scale 0
                LONG_LENGTH, SHORT_CONTENT), JsonToken.VALUE_NUMBER_FLOAT);
    }

    private final SmileMapper F_CONSTRAINED = new SmileMapper(SmileFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNumberLength(10).build())
            .build());

    private final static BigInteger LONG_BIG_INTEGER =
            new BigInteger("1234567890123456789012345678901234567890");
    private final static BigDecimal LONG_BIG_DECIMAL =
            new BigDecimal("12345678901234567890123456789012345678901.234");

    // Failures as described by _descFailure(): declared lengths in bytes
    private final static String BIG_INTEGER_FAILURE = "ERR:Number value length (17)";
    private final static String BIG_DECIMAL_FAILURE = "ERR:Number value length (19)";

    // Parsing should be able to continue after failure, with the next token
    @Test
    public void testContinueAfterFailureInArray() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = F.createGenerator(bytes)) {
            g.writeStartArray();
            g.writeNumber(LONG_BIG_INTEGER);
            g.writeNumber(LONG_BIG_DECIMAL);
            g.writeNumber(42);
            g.writeEndArray();
        }
        _verifyContinueAfterFailure(F_CONSTRAINED, bytes.toByteArray(),
                "START_ARRAY", BIG_INTEGER_FAILURE, BIG_DECIMAL_FAILURE, "42", "END_ARRAY");
    }

    @Test
    public void testContinueAfterFailureInObject() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = F.createGenerator(bytes)) {
            g.writeStartObject();
            g.writeNumberProperty("a", LONG_BIG_INTEGER);
            g.writeNumberProperty("b", LONG_BIG_DECIMAL);
            g.writeNumberProperty("c", 42);
            g.writeEndObject();
        }
        _verifyContinueAfterFailure(F_CONSTRAINED, bytes.toByteArray(),
                "START_OBJECT", "a", BIG_INTEGER_FAILURE, "b", BIG_DECIMAL_FAILURE, "c", "42", "END_OBJECT");
    }

    // Failed value should count as a token with both blocking and non-blocking
    @Test
    public void testTokenCountWithFailure() throws Exception
    {
        final SmileMapper f = new SmileMapper(SmileFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNumberLength(10).maxTokenCount(4).build())
                .build());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = F.createGenerator(bytes)) {
            g.writeStartArray();
            g.writeNumber(LONG_BIG_INTEGER);
            g.writeNumber(1);
            g.writeNumber(2);
            g.writeEndArray();
        }
        // 5 tokens: START_ARRAY, failed value, 1, 2, END_ARRAY
        _verifyContinueAfterFailure(f, bytes.toByteArray(),
                "START_ARRAY", BIG_INTEGER_FAILURE, "1", "2", "ERR:Token count (5)");
    }

    // Blocking: failure should be rethrown on further access, not internal error
    @Test
    public void testRepeatedAccessAfterFailure() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = F.createGenerator(bytes)) {
            g.writeNumber(LONG_BIG_INTEGER);
        }
        try (JsonParser p = F_CONSTRAINED.createParser(bytes.toByteArray())) {
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            StreamConstraintsException first = null;
            for (int i = 0; i < 3; ++i) {
                try {
                    if (i == 1) {
                        p.getString();
                    } else {
                        p.getNumberValue();
                    }
                    fail("Should not pass");
                } catch (StreamConstraintsException e) {
                    verifyException(e, "Number value length (17) exceeds the maximum allowed (10");
                    if (first == null) {
                        first = e;
                    } else {
                        // new exception (not same instance), with same message
                        assertNotSame(first, e);
                        assertEquals(first.getMessage(), e.getMessage());
                    }
                }
            }
            assertNull(p.nextToken());
        }
    }

    // Declared length too big to skip: same failure with blocking and non-blocking
    @Test
    public void testHugeLengthSkipAfterFailure() throws Exception
    {
        final byte[] doc = concat(HEADER, new byte[] {
                (byte) 0xF8, // START_ARRAY
                SmileConstants.TOKEN_PREFIX_INTEGER + 2,
                0x0F, 0x7F, 0x7F, 0x7F, (byte) 0xBD // 0x7FFFFFFD
        }, new byte[20]);
        final String expMsg = "Invalid content: invalid 7-bit binary encoded byte length (0x7FFFFFFD)";

        try (JsonParser p = F.createParser(doc)) {
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertThrows(StreamConstraintsException.class, () -> p.getNumberValue());
            StreamReadException e = assertThrows(StreamReadException.class, () -> p.nextToken());
            verifyException(e, expMsg);
        }
        for (int chunk : new int[] { doc.length, 1 }) {
            try (JsonParser p = F.createNonBlockingByteArrayParser()) {
                ByteArrayFeeder feeder = (ByteArrayFeeder) p.nonBlockingInputFeeder();
                int offset = 0;
                int failures = 0;
                try {
                    while (offset < doc.length) {
                        feeder.feedInput(doc, offset, Math.min(doc.length, offset + chunk));
                        offset += chunk;
                        while (true) {
                            try {
                                if (p.nextToken() == JsonToken.NOT_AVAILABLE) {
                                    break;
                                }
                            } catch (StreamConstraintsException ce) {
                                ++failures;
                            }
                        }
                    }
                    fail("Should not pass");
                } catch (StreamReadException e) {
                    assertEquals(1, failures);
                    verifyException(e, expMsg);
                }
            }
        }
    }

    // Non-blocking: if token count fails for failed value, length failure retained
    @Test
    public void testTokenCountFailureKeepsLengthFailure() throws Exception
    {
        final SmileMapper f = new SmileMapper(SmileFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNumberLength(10).maxTokenCount(1).build())
                .build());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = F.createGenerator(bytes)) {
            g.writeStartArray();
            g.writeNumber(LONG_BIG_INTEGER);
            g.writeEndArray();
        }
        final byte[] doc = bytes.toByteArray();
        try (JsonParser p = f.createNonBlockingByteArrayParser()) {
            ((ByteArrayFeeder) p.nonBlockingInputFeeder()).feedInput(doc, 0, doc.length);
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            StreamConstraintsException e = assertThrows(StreamConstraintsException.class,
                    () -> p.nextToken());
            verifyException(e, "Token count (2) exceeds the maximum allowed (1");
            assertEquals(1, e.getSuppressed().length);
            verifyException(e.getSuppressed()[0], "Number value length (17) exceeds");
        }
    }

    // Blocking: should fail without reading declared content
    @Test
    public void testFailFastWithEndlessContent() throws Exception
    {
        final byte[] head = concat(HEADER,
                new byte[] { SmileConstants.TOKEN_PREFIX_INTEGER + 2 },
                new byte[] { 0x04, 0x00, 0x00, 0x00, (byte) 0x80 }); // 2^29
        final long[] bytesRead = new long[1];
        InputStream in = new InputStream() {
            // header and type marker, then endless zero bytes
            @Override
            public int read() {
                final long pos = bytesRead[0]++;
                return (pos < head.length) ? (head[(int) pos] & 0xFF) : 0;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                for (int i = 0; i < len; ++i) {
                    b[off+i] = (byte) read();
                }
                return len;
            }
        };
        try (JsonParser p = F.createParser(in)) {
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            p.getNumberValue();
            fail("Should not pass");
        } catch (StreamConstraintsException e) {
            verifyException(e, "Number value length (536870912) exceeds the maximum allowed (1000");
        }
        assertTrue(bytesRead[0] < 100_000, "Should not read content; read "+bytesRead[0]+" bytes");
    }

    // Verifies tokens (and failures) returned by blocking and non-blocking parsers
    private void _verifyContinueAfterFailure(SmileMapper f, byte[] doc, String... expTokens)
        throws Exception
    {
        final List<String> exp = Arrays.asList(expTokens);
        // Blocking: numbers decoded lazily, on access
        List<String> tokens = new ArrayList<>();
        try (JsonParser p = f.createParser(doc)) {
            // Limit iterations to fail (not hang) if parser gets stuck
            for (int round = 0; (round < doc.length + 20) && (tokens.size() <= exp.size()); ++round) {
                JsonToken t;
                try {
                    t = p.nextToken();
                } catch (StreamConstraintsException e) {
                    tokens.add(_descFailure(e));
                    continue;
                }
                if (t == null) {
                    break;
                }
                tokens.add(_desc(p, t));
            }
        }
        assertEquals(exp, tokens, "blocking");

        // Non-blocking: fail on token itself
        for (int chunk : new int[] { doc.length, 1, 3 }) {
            tokens = new ArrayList<>();
            try (JsonParser p = f.createNonBlockingByteArrayParser()) {
                ByteArrayFeeder feeder = (ByteArrayFeeder) p.nonBlockingInputFeeder();
                int offset = 0;
                // Limit iterations to fail (not hang) if parser gets stuck
                for (int round = 0; round < 2 * doc.length + 20; ++round) {
                    JsonToken t;
                    try {
                        t = p.nextToken();
                    } catch (StreamConstraintsException e) {
                        tokens.add(_descFailure(e));
                        if (tokens.size() > exp.size()) {
                            break;
                        }
                        continue;
                    }
                    if (t == JsonToken.NOT_AVAILABLE) {
                        if (offset < doc.length) {
                            feeder.feedInput(doc, offset, Math.min(doc.length, offset + chunk));
                            offset += chunk;
                        } else {
                            feeder.endOfInput();
                        }
                        continue;
                    }
                    if (t == null) {
                        break;
                    }
                    tokens.add(_desc(p, t));
                }
            }
            assertEquals(exp, tokens, "non-blocking, chunk size "+chunk);
        }
    }

    private String _desc(JsonParser p, JsonToken t) throws Exception
    {
        if (t == JsonToken.PROPERTY_NAME) {
            return p.currentName();
        }
        if (t.isNumeric()) {
            try {
                return String.valueOf(p.getNumberValue());
            } catch (StreamConstraintsException e) {
                return _descFailure(e);
            }
        }
        return t.name();
    }

    private String _descFailure(StreamConstraintsException e)
    {
        // Start of message, up to and including first value, like
        // "ERR:Number value length (17)"
        String msg = e.getMessage();
        return "ERR:" + msg.substring(0, msg.indexOf(')') + 1);
    }

    private void _verifyEarlyFailure(byte[] doc, JsonToken expToken) throws Exception
    {
        // Blocking: decoded lazily, on access
        try (JsonParser p = F.createParser(doc)) {
            assertToken(expToken, p.nextToken());
            p.getNumberValue();
            fail("Should not pass");
        } catch (StreamConstraintsException e) {
            verifyException(e, "Number value length (8000000) exceeds the maximum allowed (1000");
        }
        // Non-blocking: both all at once and byte-by-byte
        for (int bytesPerRead : new int[] { doc.length, 1 }) {
            AsyncReaderWrapper r = asyncForBytes(F, bytesPerRead, doc, 0);
            try {
                r.nextToken();
                fail("Should not pass");
            } catch (StreamConstraintsException e) {
                verifyException(e, "Number value length (8000000) exceeds the maximum allowed (1000");
            } finally {
                r.close();
            }
        }
    }
}
