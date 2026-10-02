package com.fasterxml.jackson.dataformat.smile.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.BaseTestForSmile;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.testutil.ThrottledInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// [dataformats-binary#831]: blocking parser reported wrong byte offsets
// for input with non-zero start offset, and across input buffer boundaries
public class ParserLocationOffsets831Test extends BaseTestForSmile
{
    private final SmileFactory F = new SmileFactory();

    @Test
    public void testByteArrayWithOffset() throws Exception
    {
        byte[] doc = _smileDoc("[1, 2]");
        for (int padding : new int[] { 0, 1, 100 }) {
            byte[] buf = new byte[padding + doc.length + padding];
            System.arraycopy(doc, 0, buf, padding, doc.length);
            try (JsonParser p = F.createParser(buf, padding, doc.length)) {
                assertToken(JsonToken.START_ARRAY, p.nextToken());
                _assertOffsets(p, 4L, 5L);
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                _assertOffsets(p, 5L, 6L);
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                _assertOffsets(p, 6L, 7L);
                assertToken(JsonToken.END_ARRAY, p.nextToken());
                _assertOffsets(p, 7L, 8L);
                assertNull(p.nextToken());
                _assertOffsets(p, 8L, 8L);
            }
        }
    }

    // Single-byte ints, so offsets are known; long enough to span
    // multiple input buffers
    @Test
    public void testInputStreamSmallInts() throws Exception
    {
        final int count = 20000;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = F.createGenerator(bytes)) {
            g.writeStartArray();
            for (int i = 0; i < count; ++i) {
                g.writeNumber(i & 0x7);
            }
            g.writeEndArray();
        }
        byte[] doc = bytes.toByteArray();
        assertEquals(4 + 1 + count + 1, doc.length);

        try (JsonParser p = F.createParser(new ByteArrayInputStream(doc))) {
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            _assertOffsets(p, 4L, 5L);
            for (int i = 0; i < count; ++i) {
                assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
                _assertOffsets(p, 5L + i, 6L + i);
            }
            assertToken(JsonToken.END_ARRAY, p.nextToken());
            _assertOffsets(p, 5L + count, 6L + count);
            assertNull(p.nextToken());
            _assertOffsets(p, doc.length, doc.length);
        }
    }

    // Mixed content, with tokens spanning input buffer boundaries (and needing
    // buffer compaction); compared to offsets with a single byte[] buffer
    @Test
    public void testInputStreamMatchesByteArray() throws Exception
    {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 400; ++i) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(a2q("{'id':"+i+",'value':-1234567890123,'d':0.125,"
                    +"'name':'Some longer text value #"+i+"','big':12345678901234567890,"
                    +"'arr':[true,null,"+(i * 37)+"]}"));
        }
        sb.append("]");
        byte[] doc = _smileDoc(sb.toString());

        List<Long> exp = _offsets(F.createParser(doc), false);
        assertEquals(Long.valueOf(doc.length), exp.get(exp.size()-1));

        assertEquals(exp, _offsets(F.createParser(new ByteArrayInputStream(doc)), false));
        for (int bytesPerRead : new int[] { 1, 3, 7, 1000 }) {
            assertEquals(exp, _offsets(F.createParser(new ThrottledInputStream(doc, bytesPerRead)), false),
                    "bytesPerRead="+bytesPerRead);
        }
        // Also via `nextTextValue()` (and `nextFieldName()`), with own token-start handling
        assertEquals(exp, _offsets(F.createParser(doc), true));
        assertEquals(exp, _offsets(F.createParser(new ThrottledInputStream(doc, 3)), true));
    }

    private List<Long> _offsets(JsonParser p, boolean useNextText) throws Exception
    {
        List<Long> result = new ArrayList<>();
        try {
            JsonToken t;
            do {
                if (useNextText) {
                    if (p.getParsingContext().inObject() && !p.hasToken(JsonToken.FIELD_NAME)) {
                        p.nextFieldName();
                    } else {
                        p.nextTextValue();
                    }
                    t = p.currentToken();
                } else {
                    t = p.nextToken();
                }
                // Only token start: current location varies for lazily decoded values
                result.add(p.currentTokenLocation().getByteOffset());
            } while (t != null);
        } finally {
            p.close();
        }
        return result;
    }

    private void _assertOffsets(JsonParser p, long expTokenOffset, long expCurrOffset)
    {
        assertEquals(expTokenOffset, p.currentTokenLocation().getByteOffset());
        assertEquals(expCurrOffset, p.currentLocation().getByteOffset());
    }
}
