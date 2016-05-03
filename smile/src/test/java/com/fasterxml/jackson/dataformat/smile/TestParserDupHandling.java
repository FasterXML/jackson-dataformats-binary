package com.fasterxml.jackson.dataformat.smile;

import com.fasterxml.jackson.core.*;

public class TestParserDupHandling extends SmileTestBase
{
    public void testSimpleDups() throws Exception
    {
        for (String json : new String[] {
                "{ \"a\":1, \"a\":2 }",
                "[{ \"a\":1, \"a\":2 }]",
                "{ \"a\":1, \"b\":2, \"c\":3,\"a\":true,\"e\":false }",
                "{ \"foo\": { \"bar\": [ [ { \"x\":3, \"a\":1 } ]], \"x\":0, \"a\":\"y\", \"b\":3,\"a\":13 } }",
        }) {
            SmileFactory f = new SmileFactory();
            byte[] doc = _smileDoc(json);
            assertFalse(f.isEnabled(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
            _testSimpleDupsOk(doc, f);
    
            f.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            _testSimpleDupsFail(doc, f, "a");
        }
    }

    private void _testSimpleDupsOk(final byte[] doc, JsonFactory f) throws Exception
    {
        JsonParser jp = f.createParser(doc);
        JsonToken t = jp.nextToken();
        assertNotNull(t);
        assertTrue(t.isStructStart());
        while (jp.nextToken() != null) { }
        jp.close();
    }

    private void _testSimpleDupsFail(final byte[] doc, JsonFactory f, String name) throws Exception
    {
        JsonParser jp = f.createParser(doc);
        JsonToken t = jp.nextToken();
        assertNotNull(t);
        assertTrue(t.isStructStart());
        try {
            while (jp.nextToken() != null) { }
            fail("Should have caught dups in document: "+doc);
        } catch (JsonParseException e) {
            verifyException(e, "duplicate field '"+name+"'");
        }
        jp.close();
    }
    
}
