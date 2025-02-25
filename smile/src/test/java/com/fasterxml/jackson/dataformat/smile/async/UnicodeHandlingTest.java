package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class UnicodeHandlingTest extends AsyncTestBase
{
    @Test
    public void testShortUnicodeWithSurrogates() throws IOException
    {
        final SmileFactory f = smileFactory(false, true, false);

        // first, no buffer boundaries
        _testUnicodeWithSurrogates(f, 28, Integer.MAX_VALUE);
        _testUnicodeWithSurrogates(f, 53, Integer.MAX_VALUE);

        // then small chunks
        _testUnicodeWithSurrogates(f, 28, 3);
        _testUnicodeWithSurrogates(f, 53, 5);

        // and finally one-by-one
        _testUnicodeWithSurrogates(f, 28, 1);
        _testUnicodeWithSurrogates(f, 53, 1);
    }

    @Test
    public void testLongUnicodeWithSurrogates() throws IOException
    {
        SmileFactory f = smileFactory(false, true, false);

        _testUnicodeWithSurrogates(f, 230, Integer.MAX_VALUE);
        _testUnicodeWithSurrogates(f, 700, Integer.MAX_VALUE);
        _testUnicodeWithSurrogates(f, 9600, Integer.MAX_VALUE);

        _testUnicodeWithSurrogates(f, 230, 3);
        _testUnicodeWithSurrogates(f, 700, 3);
        _testUnicodeWithSurrogates(f, 9600, 3);

        _testUnicodeWithSurrogates(f, 230, 1);
        _testUnicodeWithSurrogates(f, 700, 1);
        _testUnicodeWithSurrogates(f, 9600, 1);
    }

    private void _testUnicodeWithSurrogates(SmileFactory f,
            int length, int readSize) throws IOException
    {
        final String SURROGATE_CHARS = "\ud834\udd1e";
        StringBuilder sb = new StringBuilder(length+200);
        while (sb.length() < length) {
            sb.append(SURROGATE_CHARS);
            sb.append(sb.length());
            if ((sb.length() & 1) == 1) {
                sb.append("\u00A3");
            } else {
                sb.append("\u3800");
            }
        }
        final String TEXT = sb.toString();
        final String quoted = quote(TEXT);
        byte[] data = _smileDoc(quoted);

        AsyncReaderWrapper r = asyncForBytes(f, readSize, data, 0);

        assertToken(JsonToken.VALUE_STRING, r.nextToken());
        assertEquals(TEXT, r.currentText());
        assertNull(r.nextToken());
        r.close();

        // Then same but skipping
        r = asyncForBytes(f, readSize, data, 0);
        assertToken(JsonToken.VALUE_STRING, r.nextToken());
        assertNull(r.nextToken());
        r.close();

        // Also, verify that it works as field name
        data = _smileDoc("{"+quoted+":true}");

        r = asyncForBytes(f, readSize, data, 0);
        assertToken(JsonToken.START_OBJECT, r.nextToken());
        assertToken(JsonToken.FIELD_NAME, r.nextToken());
        assertEquals(TEXT, r.currentName());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertToken(JsonToken.END_OBJECT, r.nextToken());
        assertNull(r.nextToken());
        r.close();

        // and skipping
        r = asyncForBytes(f, readSize, data, 0);
        assertToken(JsonToken.START_OBJECT, r.nextToken());
        assertToken(JsonToken.FIELD_NAME, r.nextToken());
        assertToken(JsonToken.VALUE_TRUE, r.nextToken());
        assertToken(JsonToken.END_OBJECT, r.nextToken());
        r.close();
    }
}
