package tools.jackson.dataformat.smile.parse;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.async.ByteArrayFeeder;

import tools.jackson.dataformat.smile.BaseTestForSmile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the "quad" decoding shared by the blocking and non-blocking Smile
 * parsers, which reads Object property names 4 bytes at a time for symbol table
 * lookup.
 *<p>
 * Trailing partial quads have two decoding paths -- a single wide load, taken
 * only when 4 bytes are readable at the offset, and byte-at-a-time shifting
 * otherwise -- so this covers every name length across the branch points (4, 8
 * and 12 bytes) and asserts that all three parser flavors agree. Parsing from a
 * {@code byte[]} exercises the shifting path for the last name in a document
 * (which ends within 3 bytes of the end of the array), while parsing the same
 * bytes from an {@link java.io.InputStream} exercises the wide load.
 */
public class NameQuadDecodingTest extends BaseTestForSmile
{
    private final static int MAX_NAME_LEN = 40;

    private final static char UNICODE_2BYTES = (char) 167; // law symbol
    private final static char UNICODE_3BYTES = (char) 0x4567;

    @Test
    public void testAsciiNamesOfEveryLength() throws Exception
    {
        for (String name : _asciiNames()) {
            _verifyName(name);
        }
    }

    @Test
    public void testUnicodeNamesOfEveryLength() throws Exception
    {
        for (String name : _unicodeNames()) {
            _verifyName(name);
        }
    }

    // Partial quads are padded with 1s, not 0s, so that a name with trailing
    // NULLs does not collide with the shorter name that precedes them
    @Test
    public void testNamesWithNulls() throws Exception
    {
        for (int len = 1; len <= MAX_NAME_LEN; ++len) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; ++i) {
                sb.append('a');
            }
            // "a", "a\0", "a\0\0", "a\0\0\0" and so on must all stay distinct
            for (int nulls = 1; nulls <= 3; ++nulls) {
                _verifyName(sb.toString() + String.valueOf(new char[nulls]));
            }
        }
    }

    // Same names within a single document, so that symbol table interning is
    // exercised across all lengths and both decoding paths
    @Test
    public void testAllNamesInSingleDocument() throws Exception
    {
        List<String> names = new ArrayList<>();
        names.addAll(_asciiNames());
        names.addAll(_unicodeNames());

        Map<String, Object> input = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); ++i) {
            input.put(names.get(i), i);
        }
        byte[] doc = smileMapper().writeValueAsBytes(input);

        for (String read : new String[] { "bytes", "stream", "async" }) {
            List<String> actual = _readNames(doc, read);
            assertEquals(names, actual, "Mismatch when reading via "+read);
        }
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    private List<String> _asciiNames()
    {
        List<String> names = new ArrayList<>();
        for (int len = 1; len <= MAX_NAME_LEN; ++len) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; ++i) {
                sb.append((char) ('a' + (i % 26)));
            }
            names.add(sb.toString());
        }
        return names;
    }

    // Multi-byte characters, so that byte length and character count differ
    private List<String> _unicodeNames()
    {
        List<String> names = new ArrayList<>();
        for (int len = 1; len <= MAX_NAME_LEN; ++len) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; ++i) {
                sb.append((i % 2 == 0) ? UNICODE_2BYTES : UNICODE_3BYTES);
            }
            names.add(sb.toString());
        }
        return names;
    }

    private void _verifyName(String name) throws Exception
    {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put(name, 13);
        byte[] doc = smileMapper().writeValueAsBytes(input);

        for (String read : new String[] { "bytes", "stream", "async" }) {
            List<String> actual = _readNames(doc, read);
            assertEquals(1, actual.size(), "Mismatch when reading via "+read);
            assertEquals(name, actual.get(0), "Mismatch when reading via "+read);
        }
    }

    private List<String> _readNames(byte[] doc, String read) throws Exception
    {
        if (read.equals("async")) {
            return _readNamesAsync(doc);
        }
        List<String> names = new ArrayList<>();
        try (JsonParser p = read.equals("bytes")
                ? _smileParser(doc)
                : _smileParser(new ByteArrayInputStream(doc))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            while (p.nextToken() == JsonToken.PROPERTY_NAME) {
                names.add(p.currentName());
                p.nextToken(); // and past the value
            }
            assertToken(JsonToken.END_OBJECT, p.currentToken());
            assertNull(p.nextToken());
        }
        return names;
    }

    // Fed one byte at a time, so names are also split across input boundaries
    private List<String> _readNamesAsync(byte[] doc) throws Exception
    {
        List<String> names = new ArrayList<>();
        try (JsonParser p = smileMapper().reader().createNonBlockingByteArrayParser()) {
            final ByteArrayFeeder feeder = (ByteArrayFeeder) p.nonBlockingInputFeeder();
            int offset = 0;
            JsonToken t;

            while (true) {
                while ((t = p.nextToken()) == JsonToken.NOT_AVAILABLE) {
                    if (offset < doc.length) {
                        assertTrue(feeder.needMoreInput());
                        feeder.feedInput(doc, offset, offset+1);
                        ++offset;
                    } else {
                        feeder.endOfInput();
                    }
                }
                if (t == null) {
                    break;
                }
                if (t == JsonToken.PROPERTY_NAME) {
                    names.add(p.currentName());
                }
            }
        }
        return names;
    }
}
