package com.fasterxml.jackson.dataformat.smile.async;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
import com.fasterxml.jackson.dataformat.smile.SmileParser;

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

    private final static SmileFactory factoryWithShared() {
        SmileFactory f = new SmileFactory();
        f.enable(SmileParser.Feature.REQUIRE_HEADER);
        f.enable(SmileGenerator.Feature.CHECK_SHARED_NAMES);
        // allow use of shared Strings too
        f.enable(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES);
        return f;
    }

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
        SmileFactory f = factoryWithShared();
        byte[] data = _stringDoc(f, input);

        // first: require headers, no offsets
        _testStrings(f, input, data, 0, 100);
        _testStrings(f, input, data, 0, 3);
        _testStrings(f, input, data, 0, 1);

        // then with some offsets:
        _testStrings(f, input, data, 1, 100);
        _testStrings(f, input, data, 1, 3);
        _testStrings(f, input, data, 1, 1);
    }

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
        SmileFactory f = factoryWithShared();
        byte[] data = _stringDoc(f, input);

        // first: require headers, no offsets
        _testStrings(f, input, data, 0, 100);
        _testStrings(f, input, data, 0, 3);
        _testStrings(f, input, data, 0, 1);

        // then with some offsets:
        _testStrings(f, input, data, 1, 100);
        _testStrings(f, input, data, 1, 3);
        _testStrings(f, input, data, 1, 1);
    }

    public void testLongAsciiStrings() throws IOException
    {
        final String[] input = new String[] {
                // ~100 chars for long(er) content
                String.format("%s %s %s %s %s %s %s %s %s %s %s %s",
                        str0to9,str0to9,"...",str0to9,"/", str0to9,
                        str0to9,"",str0to9,str0to9,"...",str0to9),
                LONG_ASCII
        };
        SmileFactory f = factoryWithShared();
        byte[] data = _stringDoc(f, input);

        // first: require headers, no offsets
        _testStrings(f, input, data, 0, 1);
        _testStrings(f, input, data, 0, 3);
        _testStrings(f, input, data, 0, 9000);

        // then with some offsets:
        _testStrings(f, input, data, 1, 9000);
        _testStrings(f, input, data, 1, 3);
        _testStrings(f, input, data, 1, 1);
    }

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
                .enable(SmileParser.Feature.REQUIRE_HEADER)
                .enable(SmileGenerator.Feature.CHECK_SHARED_NAMES)
                .enable(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES)
                .build();
        byte[] data = _stringDoc(f, input);

        AsyncReaderWrapper r = asyncForBytes(f, 1, data, 0);
        // start with "no token"
        assertNull(r.currentToken());
        assertToken(JsonToken.START_ARRAY, r.nextToken());
        assertToken(JsonToken.VALUE_STRING, r.nextToken());
        try {
            r.currentText();
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException ise) {
            assertTrue("unexpected exception message: " + ise.getMessage(),
                    ise.getMessage().startsWith("String value length (98) exceeds the maximum allowed"));
        }
    }

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
        SmileFactory f = factoryWithShared();
        byte[] data = _stringDoc(f, input);

        // first: require headers, no offsets
        _testStrings(f, input, data, 0, 9000);
        _testStrings(f, input, data, 0, 3);
        _testStrings(f, input, data, 0, 1);

        // then with some offsets:
        _testStrings(f, input, data, 1, 9000);
        _testStrings(f, input, data, 1, 3);
        _testStrings(f, input, data, 1, 1);
    }

    private void _testStrings(SmileFactory f, String[] values,
            byte[] data, int offset, int readSize) throws IOException
    {
        AsyncReaderWrapper r = asyncForBytes(f, readSize, data, offset);
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

    private byte[] _stringDoc(SmileFactory f, String[] input) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(100);
        JsonGenerator g = f.createGenerator(bytes);
        g.writeStartArray();
        for (int i = 0; i < input.length; ++i) {
            g.writeString(input[i]);
        }
        g.writeEndArray();
        g.close();
        return bytes.toByteArray();
    }
}
