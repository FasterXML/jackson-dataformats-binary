package com.fasterxml.jackson.dataformat.avro.schema;

import org.apache.avro.JsonProperties;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import static com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaHelper.jsonNodeToObject;
import static com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaHelper.objectToJsonNode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AvroSchemaHelperTest
{

	private static final JsonNodeFactory NODE_FACTORY = new ObjectMapper().getNodeFactory();

	@Test
	public void testObjectToJsonNode() {
		assertEquals(NODE_FACTORY.nullNode(), objectToJsonNode(null));
		assertEquals(NODE_FACTORY.nullNode(), objectToJsonNode(JsonProperties.NULL_VALUE));
		assertEquals(NODE_FACTORY.booleanNode(true), objectToJsonNode(true));
		assertEquals(NODE_FACTORY.booleanNode(false), objectToJsonNode(false));
		assertEquals(NODE_FACTORY.textNode("foo"), objectToJsonNode("foo"));
	}

	@Test
	public void testJsonNodeToObject() {
		assertNull(jsonNodeToObject(null));
		assertEquals(JsonProperties.NULL_VALUE, jsonNodeToObject(NODE_FACTORY.nullNode()));
		assertEquals(true, jsonNodeToObject(NODE_FACTORY.booleanNode(true)));
		assertEquals(false, jsonNodeToObject(NODE_FACTORY.booleanNode(false)));
		assertEquals("foo", jsonNodeToObject(NODE_FACTORY.textNode("foo")));
	}
}