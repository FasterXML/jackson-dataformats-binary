package com.fasterxml.jackson.dataformat.cbor;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.core.format.MatchStrength;

/**
 * Unit tests geared at testing issues that were raised due to
 * inter-operability with other CBOR codec implementations
 */
public class ParserInteropTest extends CBORTestBase
{
    private final static byte[] SELF_DESC_PLUS_TRUE = new byte[] {
        (byte) 0xD9,
        (byte) 0xD9,
        (byte) 0xF7,
        CBORConstants.BYTE_TRUE
    };

    // for [Issue#5]; Perl CBOR::XS module uses binary encoding for
    // Map/Object keys; presumably in UTF-8.
    public void testBinaryEncodedKeys() throws Exception
    {
        // from equivalent of '{"query":{} }'
        final byte[] INPUT = { (byte) 0xa1, 0x45, 0x71, 0x75, 0x65, 0x72, 0x79, (byte) 0xa0 };
        JsonParser p = cborParser(INPUT);

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("query", p.getCurrentName());
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());

        assertNull(p.nextToken());
        p.close();
    }

    // for [Issue#6]: should be fine to have self-desc tag in general
    public void testSelfDescribeTagRead() throws Exception
    {
        CBORParser p = cborParser(SELF_DESC_PLUS_TRUE);
        
        assertEquals(-1, p.getCurrentTag());

        assertToken(JsonToken.VALUE_TRUE, p.nextToken());
        assertEquals(CBORConstants.TAG_ID_SELF_DESCRIBE, p.getCurrentTag());

        assertNull(p.nextToken());
        assertEquals(-1, p.getCurrentTag());

        p.close();
    }

    // as per [Issue#6], self-describe great for format auto-detection
    public void testFormatDetection() throws Exception
    {
        CBORFactory f = cborFactory();
        // let's try to confuse auto-detector with JSON one too...
        DataFormatDetector det = new DataFormatDetector(new JsonFactory(), f);
        det = det.withMinimalMatch(MatchStrength.WEAK_MATCH).withOptimalMatch(MatchStrength.SOLID_MATCH);

        DataFormatMatcher match = det.findFormat(SELF_DESC_PLUS_TRUE);
        JsonFactory result = match.getMatch();
        assertNotNull(result);
        assertEquals("CBOR", match.getMatchedFormatName());
        assertEquals(MatchStrength.FULL_MATCH, match.getMatchStrength());

        
        // but there are other ok matches too
        match = det.findFormat(cborDoc(f, "{\"field\" :\"value\"}"));
        result = match.getMatch();
        assertNotNull(result);
        assertEquals("CBOR", match.getMatchedFormatName());
        assertEquals(MatchStrength.SOLID_MATCH, match.getMatchStrength());

        match = det.findFormat(cborDoc(f, "true"));
        result = match.getMatch();
        assertNotNull(result);
        assertEquals("CBOR", match.getMatchedFormatName());
        assertEquals(MatchStrength.SOLID_MATCH, match.getMatchStrength());
        
        // and others so-so
        match = det.findFormat(cborDoc(f, "[ 1, 2, 3 ]"));
        result = match.getMatch();
        assertNotNull(result);
        assertEquals("CBOR", match.getMatchedFormatName());
        assertEquals(MatchStrength.WEAK_MATCH, match.getMatchStrength());
    }
}
