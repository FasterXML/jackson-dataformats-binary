package tools.jackson.dataformat.smile.constraints;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.dataformat.smile.SmileFactory;
import tools.jackson.dataformat.smile.SmileMapper;
import tools.jackson.dataformat.smile.async.AsyncReaderWrapper;
import tools.jackson.dataformat.smile.async.AsyncTestBase;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#726]: `maxNameLength` was not enforced by Smile parsers
public class LongNameSmileReadTest extends AsyncTestBase
{
    private final static int MAX_NAME_LEN = 1000;

    private final SmileMapper MAPPER_VANILLA = new SmileMapper();

    private final SmileMapper MAPPER_CONSTRAINED = new SmileMapper(
            SmileFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNameLength(MAX_NAME_LEN)
                        .build())
                .build());

    // Names of 64 bytes or less use the "short name" encodings, which are
    // length-bounded by the format itself; anything longer uses the "long
    // name" encoding, which was unbounded before the fix.

    // Two separate checks guard the long-name path, and the sizes below are
    // chosen to exercise both:
    //
    // * "just over" fits within the already-allocated decode buffer, so it is
    //   only caught by the exact check made once the whole name is known;
    // * the larger ones fill the buffer and are caught incrementally, while
    //   it is being grown, before the whole name has been buffered.
    private final static int LEN_JUST_OVER = MAX_NAME_LEN + 20;
    private final static int LEN_OVER = MAX_NAME_LEN + 100;
    private final static int LEN_WAY_OVER = 400_000;

    @Test
    public void testLongNameBlocking() throws Exception
    {
        for (boolean stream : new boolean[] { true, false }) {
            for (int len : new int[] { LEN_JUST_OVER, LEN_OVER, LEN_WAY_OVER }) {
                _verifyFails(_nameDoc(len), stream);
            }
        }
    }

    @Test
    public void testLongNameAsync() throws Exception
    {
        // vary feed sizes to exercise both the single-chunk and the
        // split-across-feeds paths
        for (int bytesPerFeed : new int[] { 1, 7, 1000, 100_000 }) {
            for (int len : new int[] { LEN_JUST_OVER, LEN_OVER, LEN_WAY_OVER }) {
                _verifyFailsAsync(_nameDoc(len), bytesPerFeed);
            }
        }
    }

    // Names at or below the limit must still be accepted
    @Test
    public void testNameWithinLimitBlocking() throws Exception
    {
        for (boolean stream : new boolean[] { true, false }) {
            for (int len : new int[] { 100, MAX_NAME_LEN }) {
                _verifyPasses(_nameDoc(len), _name(len), stream);
            }
        }
    }

    @Test
    public void testNameWithinLimitAsync() throws Exception
    {
        for (int bytesPerFeed : new int[] { 1, 7, 1000, 100_000 }) {
            for (int len : new int[] { 100, MAX_NAME_LEN }) {
                _verifyPassesAsync(_nameDoc(len), _name(len), bytesPerFeed);
            }
        }
    }

    // The checks made while the decode buffer is grown are what keep an
    // over-long name from being buffered -- and decoded -- in full before it
    // gets rejected. Whether parsing fails does not show this, since the final
    // check would catch the name either way; what shows it is the length the
    // failure reports, which is how much had been read when it gave up. For a
    // name this far over the limit that has to be a small fraction of the whole.
    @Test
    public void testLongNameRejectedBeforeBufferedInFull() throws Exception
    {
        final byte[] doc = _nameDoc(LEN_WAY_OVER);

        for (boolean stream : new boolean[] { true, false }) {
            int reported = _verifyFails(doc, stream);
            assertTrue(reported < (LEN_WAY_OVER / 4),
                    "Should have given up well before reading all "+LEN_WAY_OVER
                    +" bytes of name, but reported length was "+reported);
        }
        for (int bytesPerFeed : new int[] { 1, 100_000 }) {
            int reported = _verifyFailsAsync(doc, bytesPerFeed);
            assertTrue(reported < (LEN_WAY_OVER / 4),
                    "Should have given up well before reading all "+LEN_WAY_OVER
                    +" bytes of name, but reported length was "+reported);
        }
    }

    // The symbol table is per-factory and shared by all parsers it creates, so
    // a name decoded by one parser can be served to the next straight from the
    // table, without being decoded again. The length check therefore has to
    // happen before the lookup rather than during decoding -- otherwise only
    // the very first occurrence of a name would ever be checked.
    // Both directions matter: repeated legal names must keep working, and
    // repeated over-long ones must be rejected every time.
    @Test
    public void testRepeatedNamesViaSymbolTable() throws Exception
    {
        // both sizes fit the already-allocated buffer, so the lookup, and not
        // the incremental check, is what these have to get past
        final byte[] okDoc = _nameDoc(MAX_NAME_LEN);
        final byte[] badDoc = _nameDoc(LEN_JUST_OVER);

        // repeated across parsers of the same factory: 2nd one is a cache hit
        for (int i = 0; i < 2; ++i) {
            _verifyPasses(okDoc, _name(MAX_NAME_LEN), true);
            _verifyFails(badDoc, true);
        }
    }

    // @return Name length the failure reported
    private int _verifyFails(byte[] doc, boolean stream) throws Exception
    {
        try (JsonParser p = stream
                ? MAPPER_CONSTRAINED.createParser(new ByteArrayInputStream(doc))
                : MAPPER_CONSTRAINED.createParser(doc, 0, doc.length)) {
            while (p.nextToken() != null) { }
            fail("expected StreamConstraintsException");
            return -1;
        } catch (StreamConstraintsException e) {
            return _verifyNameLengthException(e);
        }
    }

    // @return Name length the failure reported
    private int _verifyFailsAsync(byte[] doc, int bytesPerFeed) throws Exception
    {
        AsyncReaderWrapper p = asyncForBytes(MAPPER_CONSTRAINED, bytesPerFeed, doc, 0);
        try {
            while (p.nextToken() != null) { }
            fail("expected StreamConstraintsException (bytesPerFeed: "+bytesPerFeed+")");
            return -1;
        } catch (StreamConstraintsException e) {
            return _verifyNameLengthException(e);
        }
    }

    private int _verifyNameLengthException(StreamConstraintsException e)
    {
        final String msg = e.getMessage();
        assertTrue(msg.contains("Name length ("), "Unexpected message: "+msg);
        assertTrue(msg.contains("exceeds the maximum allowed ("+MAX_NAME_LEN),
                "Unexpected message: "+msg);
        int start = msg.indexOf('(') + 1;
        return Integer.parseInt(msg.substring(start, msg.indexOf(')', start)));
    }

    private void _verifyPasses(byte[] doc, String expName, boolean stream) throws Exception
    {
        try (JsonParser p = stream
                ? MAPPER_CONSTRAINED.createParser(new ByteArrayInputStream(doc))
                : MAPPER_CONSTRAINED.createParser(doc, 0, doc.length)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            assertEquals(expName, p.currentName());
            assertToken(JsonToken.VALUE_STRING, p.nextToken());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.nextToken());
        }
    }

    private void _verifyPassesAsync(byte[] doc, String expName, int bytesPerFeed) throws Exception
    {
        AsyncReaderWrapper p = asyncForBytes(MAPPER_CONSTRAINED, bytesPerFeed, doc, 0);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        assertEquals(expName, p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
    }

    private byte[] _nameDoc(int nameLen) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(nameLen + 100);
        try (JsonGenerator g = MAPPER_VANILLA.createGenerator(bytes)) {
            g.writeStartObject();
            g.writeName(_name(nameLen));
            g.writeString("v");
            g.writeEndObject();
        }
        return bytes.toByteArray();
    }

    // ASCII name, so byte length == character length
    private String _name(int len)
    {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; ++i) {
            sb.append((char) ('a' + (i % 26)));
        }
        return sb.toString();
    }
}
