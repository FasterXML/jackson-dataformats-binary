package com.fasterxml.jackson.dataformat.smile.async;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.dataformat.smile.SmileConstants;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#833]: multi-byte UTF-8 character truncated by declared
// length of String value or name must be reported, not decoded using bytes
// past end of String
public class AsyncTruncatedUTF8Test extends AsyncTestBase
{
    private final static byte[] HEADER = new byte[] {
            SmileConstants.HEADER_BYTE_1, SmileConstants.HEADER_BYTE_2,
            SmileConstants.HEADER_BYTE_3, 0
    };

    private final static byte START_ARRAY = SmileConstants.TOKEN_LITERAL_START_ARRAY;
    private final static byte END_ARRAY = SmileConstants.TOKEN_LITERAL_END_ARRAY;
    private final static byte START_OBJECT = SmileConstants.TOKEN_LITERAL_START_OBJECT;
    private final static byte END_OBJECT = SmileConstants.TOKEN_LITERAL_END_OBJECT;

    // Small ints 1 and 2
    private final static byte INT_1 = (byte) 0xC2;
    private final static byte INT_2 = (byte) 0xC4;

    private final static String VALUE_ERROR = "Truncated UTF-8 character in Short Unicode String value";
    private final static String NAME_ERROR = "Truncated UTF-8 character in Short Unicode Name";
    private final static String LONG_NAME_ERROR = "in long field name";

    private final SmileFactory F = new SmileFactory();

    /*
    /**********************************************************************
    /* Test methods, String values
    /**********************************************************************
     */

    @Test
    public void testTruncatedShortValueAtEnd() throws Exception
    {
        // Tiny Unicode, 2 bytes: 'A', then lead of 3-byte char
        _verifyFails(doc(0x80, 'A', 0xE2), VALUE_ERROR);
        // 2-byte char
        _verifyFails(doc(0x80, 'A', 0xC3), VALUE_ERROR);
        // 4-byte char, 3 bytes: 'A' 'B' then lead
        _verifyFails(doc(0x81, 'A', 'B', 0xF0), VALUE_ERROR);
        // 3-byte char, one continuation byte present but second missing
        _verifyFails(doc(0x81, 'A', 0xE2, 0x82), VALUE_ERROR);
    }

    @Test
    public void testTruncatedShortValueFollowedByContent() throws Exception
    {
        // Bytes of following ints must not be decoded as part of String
        _verifyFails(doc(START_ARRAY, 0x80, 'A', 0xE2, INT_1, INT_2, END_ARRAY),
                VALUE_ERROR);
        _verifyFails(doc(START_ARRAY, 0x81, 'A', 'B', 0xF0, INT_1, INT_2, END_ARRAY),
                VALUE_ERROR);
    }

    @Test
    public void testTruncatedSmallValue() throws Exception
    {
        // "Small" (not tiny) Unicode: 0xA0 is 34 bytes long
        byte[] content = new byte[34];
        Arrays.fill(content, (byte) 'x');
        content[33] = (byte) 0xE2;
        _verifyFails(concat(HEADER, new byte[] { START_ARRAY, (byte) 0xA0 }, content,
                new byte[] { INT_1, END_ARRAY }),
                VALUE_ERROR);
    }

    @Test
    public void testValidShortValue() throws Exception
    {
        // "Aé": 'A' + 2-byte char ending exactly at end of String
        final byte[] doc = doc(START_ARRAY, 0x81, 'A', 0xC3, 0xA9, INT_1, END_ARRAY);
        for (int bytesPerFeed : new int[] { 1, 2, 3, 1000 }) {
            for (int padding : new int[] { 0, 3 }) {
                AsyncReaderWrapper r = asyncForBytes(F, bytesPerFeed, doc, padding);
                assertToken(JsonToken.START_ARRAY, r.nextToken());
                assertToken(JsonToken.VALUE_STRING, r.nextToken());
                assertEquals("Aé", r.currentText());
                assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
                assertEquals(1, r.getIntValue());
                assertToken(JsonToken.END_ARRAY, r.nextToken());
                r.close();
            }
        }
    }

    /*
    /**********************************************************************
    /* Test methods, names
    /**********************************************************************
     */

    @Test
    public void testTruncatedShortName() throws Exception
    {
        // Short Unicode name, 2 bytes: 'A', then lead of 3-byte char
        _verifyFails(doc(START_OBJECT, 0xC0, 'A', 0xE2, INT_1, END_OBJECT),
                NAME_ERROR);
        _verifyFails(doc(START_OBJECT, 0xC1, 'A', 'B', 0xF0, INT_1, END_OBJECT),
                NAME_ERROR);
    }

    @Test
    public void testTruncatedLongName() throws Exception
    {
        // Long Unicode name: 70 x 'A', then lead of 3-byte char
        _verifyFails(longNameDoc(0xE2), LONG_NAME_ERROR);
        // 3-byte char with one of continuation bytes
        _verifyFails(longNameDoc(0xE2, 0x82), LONG_NAME_ERROR);
        // 4-byte char
        _verifyFails(longNameDoc(0xF0), LONG_NAME_ERROR);
    }

    @Test
    public void testValidLongName() throws Exception
    {
        final byte[] doc = longNameDoc(0xC3, 0xA9);
        final String expName = repeat('A', 70) + "é";
        for (int bytesPerFeed : new int[] { 1, 2, 3, 1000 }) {
            for (int padding : new int[] { 0, 3 }) {
                AsyncReaderWrapper r = asyncForBytes(F, bytesPerFeed, doc, padding);
                assertToken(JsonToken.START_OBJECT, r.nextToken());
                assertToken(JsonToken.FIELD_NAME, r.nextToken());
                assertEquals(expName, r.currentName());
                assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
                assertToken(JsonToken.END_OBJECT, r.nextToken());
                r.close();
            }
        }
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    private void _verifyFails(byte[] doc, String expError) throws Exception
    {
        // Async, with differing feed sizes; and with padding to verify that
        // bytes outside of String are not accessed (in-place decoding)
        for (int bytesPerFeed : new int[] { 1, 2, 3, 1000 }) {
            for (int padding : new int[] { 0, 3 }) {
                AsyncReaderWrapper r = asyncForBytes(F, bytesPerFeed, doc, padding);
                try {
                    StringBuilder sb = new StringBuilder();
                    JsonToken t;
                    while ((t = r.nextToken()) != null) {
                        sb.append(t);
                        if (t == JsonToken.VALUE_STRING) {
                            sb.append('(').append(r.currentText()).append(')');
                        } else if (t == JsonToken.FIELD_NAME) {
                            sb.append('(').append(r.currentName()).append(')');
                        }
                        sb.append(' ');
                    }
                    fail("Should not pass (bytesPerFeed "+bytesPerFeed+", padding "+padding
                            +"); got tokens: "+sb);
                } catch (StreamReadException e) {
                    verifyException(e, expError);
                } finally {
                    r.close();
                }
            }
        }

        // And blocking parser should report the same problem (note: String
        // values decoded lazily so need to access text)
        try (JsonParser p = _smileParser(doc)) {
            JsonToken t;
            while ((t = p.nextToken()) != null) {
                if (t == JsonToken.VALUE_STRING) {
                    p.getText();
                }
            }
            fail("Should not pass (blocking)");
        } catch (StreamReadException e) {
            verifyException(e, expError);
        }
    }

    private static byte[] doc(int... bytes)
    {
        byte[] result = Arrays.copyOf(HEADER, HEADER.length + bytes.length);
        for (int i = 0; i < bytes.length; ++i) {
            result[HEADER.length + i] = (byte) bytes[i];
        }
        return result;
    }

    // Object with a single long Unicode name: 70 x 'A' followed by given bytes
    private static byte[] longNameDoc(int... tail)
    {
        byte[] name = new byte[70 + tail.length];
        Arrays.fill(name, (byte) 'A');
        for (int i = 0; i < tail.length; ++i) {
            name[70 + i] = (byte) tail[i];
        }
        return concat(HEADER,
                new byte[] { START_OBJECT, SmileConstants.TOKEN_KEY_LONG_STRING },
                name,
                new byte[] { SmileConstants.BYTE_MARKER_END_OF_STRING, INT_1, END_OBJECT });
    }

    private static String repeat(char c, int count)
    {
        char[] ch = new char[count];
        Arrays.fill(ch, c);
        return new String(ch);
    }
}
