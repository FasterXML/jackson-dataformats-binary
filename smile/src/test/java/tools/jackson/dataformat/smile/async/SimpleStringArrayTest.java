package tools.jackson.dataformat.smile.async;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.databind.ObjectWriter;

import tools.jackson.dataformat.smile.*;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleStringArrayTest extends AsyncTestBase
{
    private final static String str0to9 = "1234567890";

    private final static String LONG_ASCII;
    static {
        int len = 12000;
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; ++i) {
            sb.append((char) ('a' + i & 31));
        }
        LONG_ASCII = sb.toString();
    }

    private final ObjectWriter WRITE_SHARED = _smileWriter(true)
        .withFeatures(SmileWriteFeature.CHECK_SHARED_NAMES,
                SmileWriteFeature.CHECK_SHARED_STRING_VALUES);

    @Test
    public void testShortAsciiStrings() throws IOException
    {

        final String[] input = new String[] {
                "Test", "", "1",
                // 60 chars, to stay short
                String.format("%s%s%s%s%s%s",
                        str0to9,str0to9,str0to9,str0to9,str0to9,str0to9,str0to9),
//                "And unicode: "+UNICODE_2BYTES+" / "+UNICODE_3BYTES,
                // plus let's do back refs:
                "Test", "124"
        };
        byte[] data = _stringDoc(WRITE_SHARED, input);

        // first: require headers, no offsets
        _testStrings(input, data, 0, 100);
        _testStrings(input, data, 0, 3);
        _testStrings(input, data, 0, 1);

        // then with some offsets:
        _testStrings(input, data, 1, 100);
        _testStrings(input, data, 1, 3);
        _testStrings(input, data, 1, 1);
    }

    @Test
    public void testShortAsciiStringAccessors() throws IOException
    {
        final int[] lengths = { 1, 2, 3, 4, 31, 32, 33, 63, 64 };
        final String[] input = new String[lengths.length];
        for (int i = 0; i < lengths.length; ++i) {
            input[i] = _ascii(lengths[i]);
        }
        byte[] data = _stringDoc(_smileWriter(true), input);

        // Contiguous input, but also chunked, to cover split-across-feeds decoding
        _testShortAsciiStringAccessors(input, data, data.length + 1);
        _testShortAsciiStringAccessors(input, data, 3);
        _testShortAsciiStringAccessors(input, data, 1);
    }

    private void _testShortAsciiStringAccessors(String[] input, byte[] data, int readSize)
        throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(_smileReader(true), readSize, data, 0);
        assertNull(r.currentToken());
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        for (String value : input) {
            assertToken(JsonToken.VALUE_STRING, r.nextToken());

            assertEquals(value, r.currentText());
            assertEquals(value.length(), r.parser().getStringLength());

            final char[] ch = r.parser().getStringCharacters();
            final int offset = r.parser().getStringOffset();
            final int len = r.parser().getStringLength();
            assertEquals(value, new String(ch, offset, len));
        }
        assertToken(JsonToken.END_ARRAY, r.nextToken());
        assertNull(r.nextToken());
        assertTrue(r.isClosed());
    }

    // [dataformats-binary#767]: short ASCII value split across feeds must decode
    // the same as one fed contiguously (it used to take the Unicode path instead)
    @Test
    public void testShortAsciiValueChunkIndependence() throws IOException
    {
        byte[] data = _stringDoc(_smileWriter(true), new String[] { "abcd" });
        // Corrupt one content byte so ASCII and Unicode decoding disagree
        int ix = _lastIndexOf(data, (byte) 'b');
        assertTrue(ix > 0, "Should find content byte to corrupt");
        data[ix] = (byte) 0xC5;

        String contiguous = _readSingleString(data, data.length + 1);
        assertEquals(contiguous, _readSingleString(data, 3));
        assertEquals(contiguous, _readSingleString(data, 1));
    }

    private String _readSingleString(byte[] data, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(_smileReader(true), readSize, data, 0);
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        assertToken(JsonToken.VALUE_STRING, r.nextToken());
        String text = r.currentText();
        assertToken(JsonToken.END_ARRAY, r.nextToken());
        r.close();
        return text;
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

    @Test
    public void testShortUnicodeStrings() throws IOException
    {
        final String repeat = "Test: "+UNICODE_2BYTES;
        final String[] input = new String[] {
                repeat, "",
                ""+UNICODE_3BYTES,
                ""+UNICODE_2BYTES,
                // 60 chars, to stay short
                String.format("%s %c %s %c %s",
                        str0to9, UNICODE_3BYTES,
                        str0to9, UNICODE_2BYTES, str0to9),
                "Test", repeat,
                "!"
        };
        byte[] data = _stringDoc(WRITE_SHARED, input);

        // first: require headers, no offsets
        _testStrings(input, data, 0, 100);
        _testStrings(input, data, 0, 3);
        _testStrings(input, data, 0, 1);

        // then with some offsets:
        _testStrings(input, data, 1, 100);
        _testStrings(input, data, 1, 3);
        _testStrings(input, data, 1, 1);
    }

    @Test
    public void testLongAsciiStrings() throws IOException
    {
        final String[] input = new String[] {
                // ~100 chars for long(er) content
                String.format("%s %s %s %s %s %s %s %s %s %s %s %s",
                        str0to9,str0to9,"...",str0to9,"/", str0to9,
                        str0to9,"",str0to9,str0to9,"...",str0to9),
                LONG_ASCII
        };
        byte[] data = _stringDoc(WRITE_SHARED, input);

        // first: require headers, no offsets
        _testStrings(input, data, 0, 1);
        _testStrings(input, data, 0, 3);
        _testStrings(input, data, 0, 9000);

        // then with some offsets:
        _testStrings(input, data, 1, 9000);
        _testStrings(input, data, 1, 3);
        _testStrings(input, data, 1, 1);
    }

    @Test
    public void testLongAsciiStringsLowStringLimit() throws IOException
    {
        final String[] input = new String[] {
                // ~100 chars for long(er) content
                String.format("%s %s %s %s %s %s %s %s %s %s %s %s",
                        str0to9,str0to9,"...",str0to9,"/", str0to9,
                        str0to9,"",str0to9,str0to9,"...",str0to9),
                LONG_ASCII
        };
        SmileFactory f = SmileFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(10).build())
                .enable(SmileReadFeature.REQUIRE_HEADER)
                .enable(SmileWriteFeature.CHECK_SHARED_NAMES)
                .enable(SmileWriteFeature.CHECK_SHARED_STRING_VALUES)
                .build();
        SmileMapper mapper = new SmileMapper(f);
        byte[] data = _stringDoc(mapper.writer(), input);

        AsyncReaderWrapper r = asyncForBytes(mapper, 1, data, 0);
        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        assertToken(JsonToken.VALUE_STRING, r.nextToken());
        try {
            r.currentText();
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException ise) {
            assertTrue(ise.getMessage().startsWith("String value length (98) exceeds the maximum allowed"),
                    "unexpected exception message: " + ise.getMessage());
        }
    }

    @Test
    public void testLongUnicodeStrings() throws IOException
    {
        // ~100 chars for long(er) content
        final String LONG = String.format("%s %s %s %s %s%s %s %s %s %s %s %s%c %s",
                str0to9,str0to9,UNICODE_2BYTES,str0to9,UNICODE_3BYTES,UNICODE_3BYTES, str0to9,
                str0to9,UNICODE_3BYTES,str0to9,str0to9,UNICODE_2BYTES,UNICODE_2BYTES,str0to9);

        final String[] input = new String[] {
                // let's vary length slightly to try to trigger edge conditions
                LONG,
                LONG + ".",
                LONG + "..",
                LONG + "..."
        };
        byte[] data = _stringDoc(WRITE_SHARED, input);

        // first: require headers, no offsets
        _testStrings(input, data, 0, 9000);
        _testStrings(input, data, 0, 3);
        _testStrings(input, data, 0, 1);

        // then with some offsets:
        _testStrings(input, data, 1, 9000);
        _testStrings(input, data, 1, 3);
        _testStrings(input, data, 1, 1);
    }

    private void _testStrings(String[] values,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(_smileReader(true), readSize, data, offset);
        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        for (int i = 0; i < values.length; ++i) {
            assertToken(JsonToken.VALUE_STRING, r.nextToken());
            assertEquals(values[i], r.currentText());

            // 13-May-2017, tatu: Rules of whether efficient char[] does or does not
            //    exist vary... So let's NOT try to determine at this point.
//            assertTrue(r.parser().hasTextCharacters());
        }
        assertToken(JsonToken.END_ARRAY, r.nextToken());

        // and end up with "no token" as well
        assertNull(r.nextToken());
        assertTrue(r.isClosed());
    }

    private byte[] _stringDoc(ObjectWriter w, String[] input) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(100);
        JsonGenerator g = w.createGenerator(bytes);
        g.writeStartArray();
        for (int i = 0; i < input.length; ++i) {
            g.writeString(input[i]);
        }
        g.writeEndArray();
        g.close();
        return bytes.toByteArray();
    }

    private String _ascii(int len)
    {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; ++i) {
            sb.append((char) ('a' + (i % 26)));
        }
        return sb.toString();
    }
}
