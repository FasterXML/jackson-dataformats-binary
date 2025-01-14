package tools.jackson.dataformat.cbor.parse;

import org.junit.jupiter.api.Test;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.ObjectReader;
import tools.jackson.dataformat.cbor.CBORTestBase;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ParserDupHandlingTest extends CBORTestBase
{
    @Test
    public void testSimpleDups()
    {
        for (String json : new String[] {
                "{ \"a\":1, \"a\":2 }",
                "[{ \"a\":1, \"a\":2 }]",
                "{ \"a\":1, \"b\":2, \"c\":3,\"a\":true,\"e\":false }",
                "{ \"foo\": { \"bar\": [ [ { \"x\":3, \"a\":1 } ]], \"x\":0, \"a\":\"y\", \"b\":3,\"a\":13 } }",
        }) {
            byte[] doc = cborDoc(json);
            ObjectReader r = sharedMapper().reader();
            _testSimpleDupsOk(doc, r.without(StreamReadFeature.STRICT_DUPLICATE_DETECTION));
            _testSimpleDupsFail(doc,
                    r.with(StreamReadFeature.STRICT_DUPLICATE_DETECTION),
                    "a");
        }
    }

    private void _testSimpleDupsOk(final byte[] doc, ObjectReader r)
    {
        JsonParser p = r.createParser(doc);
        JsonToken t = p.nextToken();
        assertNotNull(t);
        assertTrue(t.isStructStart());
        while (p.nextToken() != null) { }
        p.close();
    }

    private void _testSimpleDupsFail(final byte[] doc, ObjectReader r, String name)
    {
        JsonParser p = r.createParser(doc);
        JsonToken t = p.nextToken();
        assertNotNull(t);
        assertTrue(t.isStructStart());
        try {
            while (p.nextToken() != null) { }
            fail("Should have caught dups in document: "+doc);
        } catch (StreamReadException e) {
            verifyException(e, "duplicate Object property \""+name+"\"");
        }
        p.close();
    }
}
