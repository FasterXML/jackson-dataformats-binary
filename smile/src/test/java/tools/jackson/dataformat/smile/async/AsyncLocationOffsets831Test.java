package tools.jackson.dataformat.smile.async;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.async.ByteArrayFeeder;
import tools.jackson.dataformat.smile.SmileConstants;
import tools.jackson.dataformat.smile.SmileMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// [dataformats-binary#831]: non-blocking parser reported wrong byte offsets
// for both current location and current token location
public class AsyncLocationOffsets831Test extends AsyncTestBase
{
    private final SmileMapper MAPPER = newSmileMapper();

    @Test
    public void testSimpleArray() throws Exception
    {
        byte[] doc = _smileDoc("[1, 2]");
        // header (4 bytes), then START_ARRAY, 2 ints, END_ARRAY
        for (int padding : new int[] { 0, 1, 100 }) {
            AsyncReaderWrapper r = asyncForBytes(MAPPER, 1000, doc, padding);
            assertToken(JsonToken.START_ARRAY, r.nextToken());
            _assertOffsets(r.parser(), 4L, 5L);
            assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
            _assertOffsets(r.parser(), 5L, 6L);
            assertToken(JsonToken.VALUE_NUMBER_INT, r.nextToken());
            _assertOffsets(r.parser(), 6L, 7L);
            assertToken(JsonToken.END_ARRAY, r.nextToken());
            _assertOffsets(r.parser(), 7L, 8L);
            r.close();
        }
    }

    // Compare against blocking parser, with tokens that span chunks
    @Test
    public void testTokenOffsetsMatchBlocking() throws Exception
    {
        byte[] doc = _smileDoc(a2q("{'a':1,'name':'some longer String value here','arr':[true,null,-12345678,"
                +"0.25,12345678901234567890,'"+UNICODE_SEGMENT+"'],'nested':{'x':'y','a':[]}}"));
        List<Long> exp = _blockingTokenOffsets(doc);
        for (int chunk : new int[] { 1, 2, 3, 5, 7, 1000 }) {
            for (int padding : new int[] { 0, 1, 17 }) {
                AsyncReaderWrapper r = asyncForBytes(MAPPER, chunk, doc, padding);
                List<Long> act = new ArrayList<>();
                JsonToken t;
                do {
                    t = r.nextToken();
                    act.add(r.parser().currentTokenLocation().getByteOffset());
                } while (t != null);
                assertEquals(exp, act, "chunk="+chunk+", padding="+padding);
                // and at the end, should have consumed all content
                assertEquals((long) doc.length, r.parser().currentLocation().getByteOffset(),
                        "chunk="+chunk+", padding="+padding);
                r.close();
            }
        }
    }

    // Header ending exactly at chunk boundary: token location should not point
    // to the header while waiting for more content
    @Test
    public void testHeaderAtChunkBoundary() throws Exception
    {
        byte[] doc = _smileDoc("[1, 2]");
        try (JsonParser p = MAPPER.createNonBlockingByteArrayParser()) {
            ByteArrayFeeder feeder = (ByteArrayFeeder) p.nonBlockingInputFeeder();
            byte[] buf = new byte[10 + doc.length];
            System.arraycopy(doc, 0, buf, 10, doc.length);
            feeder.feedInput(buf, 10, 14); // just the header
            assertToken(JsonToken.NOT_AVAILABLE, p.nextToken());
            _assertOffsets(p, 4L, 4L);
            feeder.feedInput(buf, 14, buf.length);
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            _assertOffsets(p, 4L, 5L);
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            _assertOffsets(p, 5L, 6L);
        }
    }

    // [dataformats-binary#838]: same for in-line header right after end marker
    // (not reported as separate `null` token) ending at chunk boundary
    @Test
    public void testInlineHeaderAfterEndMarkerAtChunkBoundary() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(_smileDoc("12")); // header (4 bytes), int (1 byte)
        bytes.write(SmileConstants.BYTE_MARKER_END_OF_CONTENT);
        bytes.write(_smileDoc("[1]")); // header (4 bytes), START_ARRAY, int, END_ARRAY
        byte[] doc = bytes.toByteArray();

        try (JsonParser p = MAPPER.createNonBlockingByteArrayParser()) {
            ByteArrayFeeder feeder = (ByteArrayFeeder) p.nonBlockingInputFeeder();
            byte[] buf = new byte[10 + doc.length];
            System.arraycopy(doc, 0, buf, 10, doc.length);
            feeder.feedInput(buf, 10, 20); // up to and including second header
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            _assertOffsets(p, 4L, 5L);
            assertNull(p.nextToken()); // end marker
            _assertOffsets(p, 5L, 6L);
            assertToken(JsonToken.NOT_AVAILABLE, p.nextToken());
            _assertOffsets(p, 10L, 10L);
            feeder.feedInput(buf, 20, buf.length);
            assertToken(JsonToken.START_ARRAY, p.nextToken());
            _assertOffsets(p, 10L, 11L);
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            _assertOffsets(p, 11L, 12L);
        }
    }

    // Multiple documents, separated by in-line headers (and with end marker
    // at the end): compare against blocking parser
    @Test
    public void testMultipleDocsMatchBlocking() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(_smileDoc(a2q("{'a':'value','b':[1,2.5,'xyz']}")));
        bytes.write(_smileDoc(a2q("['some longer String value here',-123456789012345]")));
        bytes.write(_smileDoc(a2q("'"+UNICODE_SEGMENT+"'")));
        bytes.write(_smileDoc("[true,false]"));
        bytes.write(_smileDoc(a2q("{'x':{'y':null}}")));
        // NOTE: end marker only at the end; see
        // `testEndMarkersAndHeadersMatchBlocking()` for end markers between documents
        bytes.write(SmileConstants.BYTE_MARKER_END_OF_CONTENT);
        byte[] doc = bytes.toByteArray();

        List<String> exp = new ArrayList<>();
        try (JsonParser p = _smileParser(doc)) {
            _collectTokens(p, exp);
        }
        // sanity check: ends at end of content
        assertEquals("null@"+doc.length, exp.get(exp.size()-1));
        // and has a `null` token after each document (plus one for end marker)
        assertEquals(6L, exp.stream().filter(str -> str.startsWith("null@")).count(),
                "Should have `null` token after each document, got: "+exp);

        for (int chunk : new int[] { 1, 2, 3, 5, 7, 1000 }) {
            for (int padding : new int[] { 0, 1, 17 }) {
                AsyncReaderWrapper r = asyncForBytes(MAPPER, chunk, doc, padding);
                List<String> act = new ArrayList<>();
                // `AsyncReaderWrapper` handles NOT_AVAILABLE (feeding more)
                JsonToken t;
                do {
                    t = r.nextToken();
                    act.add(t+"@"+r.parser().currentTokenLocation().getByteOffset());
                } while ((t != null) || !r.parser().isClosed());
                assertEquals(exp, act, "chunk="+chunk+", padding="+padding);
                r.close();
            }
        }
    }

    // [dataformats-binary#838]: end markers followed by (possibly repeated)
    // in-line headers, after root-level scalars and Arrays: token locations
    // (including `null` tokens) should match blocking parser
    @Test
    public void testEndMarkersAndHeadersMatchBlocking() throws Exception
    {
        final byte[] header = _smileDoc("[]");
        final int headerLen = 4;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(_smileDoc("12"));
        bytes.write(SmileConstants.BYTE_MARKER_END_OF_CONTENT);
        bytes.write(_smileDoc(a2q("'abc'")));
        bytes.write(SmileConstants.BYTE_MARKER_END_OF_CONTENT);
        // repeated headers
        bytes.write(header, 0, headerLen);
        bytes.write(header, 0, headerLen);
        bytes.write(_smileDoc("[1,2]"));
        bytes.write(SmileConstants.BYTE_MARKER_END_OF_CONTENT);
        bytes.write(_smileDoc("true"));
        bytes.write(_smileDoc("-1234567"));
        bytes.write(SmileConstants.BYTE_MARKER_END_OF_CONTENT);
        byte[] doc = bytes.toByteArray();

        List<String> exp = new ArrayList<>();
        try (JsonParser p = _smileParser(doc)) {
            _collectTokens(p, exp);
        }
        assertEquals("null@"+doc.length, exp.get(exp.size()-1));

        for (int chunk : new int[] { 1, 2, 3, 5, 7, 1000 }) {
            for (int padding : new int[] { 0, 1, 17 }) {
                AsyncReaderWrapper r = asyncForBytes(MAPPER, chunk, doc, padding);
                List<String> act = new ArrayList<>();
                JsonToken t;
                do {
                    t = r.nextToken();
                    act.add(t+"@"+r.parser().currentTokenLocation().getByteOffset());
                } while ((t != null) || !r.parser().isClosed());
                assertEquals(exp, act, "chunk="+chunk+", padding="+padding);
                r.close();
            }
        }
    }

    private void _collectTokens(JsonParser p, List<String> tokens) throws Exception
    {
        JsonToken t;
        do {
            t = p.nextToken();
            tokens.add(t+"@"+p.currentTokenLocation().getByteOffset());
        } while ((t != null) || !p.isClosed());
    }

    private List<Long> _blockingTokenOffsets(byte[] doc) throws Exception
    {
        List<Long> offsets = new ArrayList<>();
        try (JsonParser p = _smileParser(doc)) {
            JsonToken t;
            do {
                t = p.nextToken();
                offsets.add(p.currentTokenLocation().getByteOffset());
            } while (t != null);
        }
        // sanity check: end-of-input "token" located at the end of content
        assertEquals(Long.valueOf(doc.length), offsets.get(offsets.size()-1));
        return offsets;
    }

    private void _assertOffsets(JsonParser p, long expTokenOffset, long expCurrOffset)
    {
        assertEquals(expTokenOffset, p.currentTokenLocation().getByteOffset());
        assertEquals(expCurrOffset, p.currentLocation().getByteOffset());
    }
}
