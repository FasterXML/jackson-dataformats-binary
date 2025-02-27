package tools.jackson.dataformat.avro.schema;

import org.apache.avro.JsonProperties;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static tools.jackson.dataformat.avro.schema.AvroSchemaHelper.jsonNodeToObject;
import static tools.jackson.dataformat.avro.schema.AvroSchemaHelper.objectToJsonNode;

public class AvroSchemaHelperTest
{
	private static final JsonNodeFactory NODE_FACTORY = new ObjectMapper().getNodeFactory();

	@Test
	public void testObjectToJsonNode() {
		assertEquals(NODE_FACTORY.nullNode(), objectToJsonNode(null));
		assertEquals(NODE_FACTORY.nullNode(), objectToJsonNode(JsonProperties.NULL_VALUE));
		assertEquals(NODE_FACTORY.booleanNode(true), objectToJsonNode(true));
		assertEquals(NODE_FACTORY.booleanNode(false), objectToJsonNode(false));
		assertEquals(NODE_FACTORY.stringNode("foo"), objectToJsonNode("foo"));
	}

	@Test
	public void testJsonNodeToObject() {
		assertNull(jsonNodeToObject(null));
		assertEquals(JsonProperties.NULL_VALUE, jsonNodeToObject(NODE_FACTORY.nullNode()));
		assertEquals(true, jsonNodeToObject(NODE_FACTORY.booleanNode(true)));
		assertEquals(false, jsonNodeToObject(NODE_FACTORY.booleanNode(false)));
		assertEquals("foo", jsonNodeToObject(NODE_FACTORY.stringNode("foo")));
	}
}
