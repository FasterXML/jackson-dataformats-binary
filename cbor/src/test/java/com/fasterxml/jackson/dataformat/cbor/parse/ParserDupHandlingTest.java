package com.fasterxml.jackson.dataformat.cbor.parse;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORTestBase;

public class ParserDupHandlingTest extends CBORTestBase
{
    public void testSimpleDups() throws Exception
    {
        for (String json : new String[] {
                "{ \"a\":1, \"a\":2 }",
                "[{ \"a\":1, \"a\":2 }]",
                "{ \"a\":1, \"b\":2, \"c\":3,\"a\":true,\"e\":false }",
                "{ \"foo\": { \"bar\": [ [ { \"x\":3, \"a\":1 } ]], \"x\":0, \"a\":\"y\", \"b\":3,\"a\":13 } }",
        }) {
            CBORFactory f = new CBORFactory();
            byte[] doc = cborDoc(f, json);
            assertFalse(f.isEnabled(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
            _testSimpleDupsOk(doc, f);
    
            f.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            _testSimpleDupsFail(doc, f, "a");
        }
    }

    private void _testSimpleDupsOk(final byte[] doc, JsonFactory f) throws Exception
    {
        JsonParser p = f.createParser(doc);
        JsonToken t = p.nextToken();
        assertNotNull(t);
        assertTrue(t.isStructStart());
        while (p.nextToken() != null) { }
        p.close();
    }

    private void _testSimpleDupsFail(final byte[] doc, JsonFactory f, String name) throws Exception
    {
        JsonParser p = f.createParser(doc);
        JsonToken t = p.nextToken();
        assertNotNull(t);
        assertTrue(t.isStructStart());
        try {
            while (p.nextToken() != null) { }
            fail("Should have caught dups in document: "+doc);
        } catch (JsonParseException e) {
            verifyException(e, "duplicate field '"+name+"'");
        }
        p.close();
    }
    
}
