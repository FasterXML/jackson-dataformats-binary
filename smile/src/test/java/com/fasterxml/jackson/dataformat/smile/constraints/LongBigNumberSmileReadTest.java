package com.fasterxml.jackson.dataformat.smile.constraints;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;

import com.fasterxml.jackson.dataformat.smile.SmileConstants;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.async.AsyncReaderWrapper;
import com.fasterxml.jackson.dataformat.smile.async.AsyncTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private final SmileFactory F = new SmileFactory();

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

    // Parsing should be able to continue after failure, with the next token
    @Test
    public void testContinueAfterFailure() throws Exception
    {
        final SmileFactory constrained = SmileFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNumberLength(10).build())
                .build();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = F.createGenerator(bytes)) {
            g.writeStartArray();
            g.writeNumber(new BigInteger("1234567890123456789012345678901234567890"));
            g.writeNumber(new BigDecimal("12345678901234567890123456789012345678901.234"));
            g.writeNumber(42);
            g.writeEndArray();
        }
        final byte[] doc = bytes.toByteArray();

        // Blocking: numbers decoded lazily, on access
        List<String> tokens = new ArrayList<>();
        try (JsonParser p = constrained.createParser(doc)) {
            JsonToken t;
            while ((t = p.nextToken()) != null) {
                tokens.add(_desc(p, t));
            }
        }
        assertEquals(Arrays.asList("START_ARRAY", "ERR", "ERR", "42", "END_ARRAY"), tokens);

        // Non-blocking: fail on token itself
        for (int chunk : new int[] { doc.length, 1, 3 }) {
            tokens = new ArrayList<>();
            try (JsonParser p = constrained.createNonBlockingByteArrayParser()) {
                ByteArrayFeeder feeder = (ByteArrayFeeder) p.getNonBlockingInputFeeder();
                int offset = 0;
                while (true) {
                    JsonToken t;
                    try {
                        t = p.nextToken();
                    } catch (StreamConstraintsException e) {
                        tokens.add("ERR");
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
            assertEquals(Arrays.asList("START_ARRAY", "ERR", "ERR", "42", "END_ARRAY"), tokens,
                    "chunk size "+chunk);
        }
    }

    private String _desc(JsonParser p, JsonToken t) throws Exception
    {
        if (t.isNumeric()) {
            try {
                return String.valueOf(p.getNumberValue());
            } catch (StreamConstraintsException e) {
                return "ERR";
            }
        }
        return t.name();
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
