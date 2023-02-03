package tools.jackson.dataformat.cbor.mapper;

import java.util.*;

import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.cbor.CBORTestBase;

/**
 * This unit test suite tries to verify that ObjectMapper
 * can properly parse JSON and bind contents into appropriate
 * JsonNode instances.
 */
public class TreeReadViaMapperTest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    public void testSimple() throws Exception
    {
        JsonNode result = MAPPER.readTree(cborDoc(SAMPLE_DOC_JSON_SPEC));

        assertType(result, ObjectNode.class);
        assertEquals(1, result.size());
        assertTrue(result.isObject());

        ObjectNode main = (ObjectNode) result;
        assertEquals("Image", main.propertyNames().next());
        JsonNode ob = main.elements().next();
        assertType(ob, ObjectNode.class);
        ObjectNode imageMap = (ObjectNode) ob;

        assertEquals(5, imageMap.size());
        ob = imageMap.get("Width");
        assertTrue(ob.isIntegralNumber());
        assertFalse(ob.isFloatingPointNumber());
        assertEquals(SAMPLE_SPEC_VALUE_WIDTH, ob.intValue());
        ob = imageMap.get("Height");
        assertTrue(ob.isIntegralNumber());
        assertEquals(SAMPLE_SPEC_VALUE_HEIGHT, ob.intValue());

        ob = imageMap.get("Title");
        assertTrue(ob.isTextual());
        assertEquals(SAMPLE_SPEC_VALUE_TITLE, ob.textValue());

        ob = imageMap.get("Thumbnail");
        assertType(ob, ObjectNode.class);
        ObjectNode tn = (ObjectNode) ob;
        ob = tn.get("Url");
        assertTrue(ob.isTextual());
        assertEquals(SAMPLE_SPEC_VALUE_TN_URL, ob.textValue());
        ob = tn.get("Height");
        assertTrue(ob.isIntegralNumber());
        assertEquals(SAMPLE_SPEC_VALUE_TN_HEIGHT, ob.intValue());
        ob = tn.get("Width");
        assertTrue(ob.isTextual());
        assertEquals(SAMPLE_SPEC_VALUE_TN_WIDTH, ob.textValue());

        ob = imageMap.get("IDs");
        assertTrue(ob.isArray());
        ArrayNode idList = (ArrayNode) ob;
        assertEquals(4, idList.size());
        assertEquals(4, calcLength(idList.elements()));
        assertEquals(4, calcLength(idList.iterator()));
        {
            int[] values = new int[] {
                SAMPLE_SPEC_VALUE_TN_ID1,
                SAMPLE_SPEC_VALUE_TN_ID2,
                SAMPLE_SPEC_VALUE_TN_ID3,
                SAMPLE_SPEC_VALUE_TN_ID4
            };
            for (int i = 0; i < values.length; ++i) {
                assertEquals(values[i], idList.get(i).intValue());
            }
            int i = 0;
            for (JsonNode n : idList) {
                assertEquals(values[i], n.intValue());
                ++i;
            }
        }
    }

    public void testMultiple() throws Exception
    {
        JsonParser p = MAPPER.createParser(cborDoc("12  \"string\" [ 1, 2, 3 ]"));
        JsonNode result = MAPPER.readTree(p);

        assertTrue(result.isIntegralNumber());
        assertTrue(result.isInt());
        assertFalse(result.isTextual());
        assertEquals(12, result.intValue());

        result = MAPPER.readTree(p);
        assertTrue(result.isTextual());
        assertFalse(result.isIntegralNumber());
        assertFalse(result.isInt());
        assertEquals("string", result.textValue());

        result = MAPPER.readTree(p);
        assertTrue(result.isArray());
        assertEquals(3, result.size());

        assertNull(MAPPER.readTree(p));
        p.close();
    }

    /*
    /**********************************************
    /* Helper methods
    /**********************************************
     */

    private int calcLength(Iterator<JsonNode> it)
    {
        int count = 0;
        while (it.hasNext()) {
            it.next();
            ++count;
        }
        return count;
    }
}

