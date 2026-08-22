package tools.jackson.dataformat.smile.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.dataformat.smile.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for [dataformats-binary#759]: "long" (64+ byte) text values decoded
 * straight from input bytes when contiguous and all-ASCII. Covers both
 * `TOKEN_BYTE_LONG_STRING_ASCII` and `TOKEN_MISC_LONG_TEXT_UNICODE` token
 * types -- generator is forced to use the latter for text too long to
 * speculate on, even when content is pure ASCII.
 */
public class LongTextDecode759Test
    extends BaseTestForSmile
{
    // Lengths straddling the point (~2666 chars) where the generator can no
    // longer speculate on ASCII-ness and switches to the "Unicode" token type
    private final static int[] LENGTHS = { 64, 65, 100, 1000, 2665, 2666, 2667, 3000, 9000 };

    @Test
    public void testLongAsciiValues() throws Exception
    {
        for (int len : LENGTHS) {
            String text = _ascii(len);
            _verifyRoundTrip(text);
        }
    }

    @Test
    public void testLongValuesWithNonAsciiAtEveryBoundary() throws Exception
    {
        // non-ASCII char at start/middle/end must defeat the fast path cleanly
        for (int len : LENGTHS) {
            for (int pos : new int[] { 0, 1, len / 2, len - 1 }) {
                StringBuilder sb = new StringBuilder(_ascii(len));
                sb.setCharAt(pos, '中');
                _verifyRoundTrip(sb.toString());
            }
        }
    }

    @Test
    public void testLongValuesWithSurrogatePair() throws Exception
    {
        for (int len : LENGTHS) {
            StringBuilder sb = new StringBuilder(_ascii(len - 2));
            sb.appendCodePoint(0x1F601);
            _verifyRoundTrip(sb.toString());
        }
    }

    // Values spanning buffer reloads must fall back to general handling
    @Test
    public void testLongValuesFromChunkedStream() throws Exception
    {
        List<String> texts = new ArrayList<>();
        for (int len : LENGTHS) {
            texts.add(_ascii(len));
        }
        byte[] doc = _doc(texts);
        for (int chunkSize : new int[] { 1, 7, 999 }) {
            _verifyDoc(texts, new ChunkedStream(doc, chunkSize));
        }
    }

    private void _verifyRoundTrip(String text) throws Exception
    {
        List<String> texts = new ArrayList<>();
        texts.add(text);
        _verifyDoc(texts, null); // byte[] backed
        _verifyDoc(texts, new ByteArrayInputStream(_doc(texts)));
    }

    private void _verifyDoc(List<String> expected, InputStream in) throws Exception
    {
        try (JsonParser p = (in == null) ? _smileParser(_doc(expected)) : _smileParser(in)) {
            assertEquals(JsonToken.START_ARRAY, p.nextToken());
            for (String exp : expected) {
                assertEquals(JsonToken.VALUE_STRING, p.nextToken());
                assertEquals(exp, p.getString());
            }
            assertEquals(JsonToken.END_ARRAY, p.nextToken());
            assertNull(p.nextToken());
        }
    }

    private byte[] _doc(List<String> texts) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SmileFactory f = smileFactory(false, false, false);
        try (JsonGenerator g = f.createGenerator(ObjectWriteContext.empty(), out)) {
            g.writeStartArray();
            for (String text : texts) {
                g.writeString(text);
            }
            g.writeEndArray();
        }
        return out.toByteArray();
    }

    private String _ascii(int len)
    {
        Random r = new Random(len);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; ++i) {
            sb.append((char) ('a' + r.nextInt(26)));
        }
        return sb.toString();
    }

    // Yields small chunks, forcing buffer reloads in the middle of values
    static class ChunkedStream extends InputStream
    {
        private final byte[] _data;
        private final int _chunkSize;
        private int _ptr;

        public ChunkedStream(byte[] data, int chunkSize) {
            _data = data;
            _chunkSize = chunkSize;
        }

        @Override
        public int read() {
            return (_ptr < _data.length) ? (_data[_ptr++] & 0xFF) : -1;
        }

        @Override
        public int read(byte[] buffer, int offset, int len) {
            if (_ptr >= _data.length) {
                return -1;
            }
            int count = Math.min(Math.min(len, _chunkSize), _data.length - _ptr);
            System.arraycopy(_data, _ptr, buffer, offset, count);
            _ptr += count;
            return count;
        }
    }
}
