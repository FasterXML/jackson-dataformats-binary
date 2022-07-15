package tools.jackson.dataformat.avro.schema;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import junit.framework.TestCase;

import static tools.jackson.dataformat.avro.schema.AvroSchemaHelper.jsonNodeToObject;
import static tools.jackson.dataformat.avro.schema.AvroSchemaHelper.objectToJsonNode;

import org.apache.avro.JsonProperties;

public class AvroSchemaHelperTest extends TestCase {
	private static final JsonNodeFactory NODE_FACTORY = new ObjectMapper().getNodeFactory();

	public void testObjectToJsonNode() {
		assertEquals(NODE_FACTORY.nullNode(), objectToJsonNode(null));
		assertEquals(NODE_FACTORY.nullNode(), objectToJsonNode(JsonProperties.NULL_VALUE));
		assertEquals(NODE_FACTORY.booleanNode(true), objectToJsonNode(true));
		assertEquals(NODE_FACTORY.booleanNode(false), objectToJsonNode(false));
		assertEquals(NODE_FACTORY.textNode("foo"), objectToJsonNode("foo"));
	}

	public void testJsonNodeToObject() {
		assertNull(jsonNodeToObject(null));
		assertEquals(JsonProperties.NULL_VALUE, jsonNodeToObject(NODE_FACTORY.nullNode()));
		assertEquals(true, jsonNodeToObject(NODE_FACTORY.booleanNode(true)));
		assertEquals(false, jsonNodeToObject(NODE_FACTORY.booleanNode(false)));
		assertEquals("foo", jsonNodeToObject(NODE_FACTORY.textNode("foo")));
	}
}
