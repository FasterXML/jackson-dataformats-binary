package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class EnumWithJavaReservedKeywordsTest extends AvroTestBase {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private String JAVA_RESERVED_KEYWORD_SCHEMA_JSON = "{\n" +
			"\"type\": \"record\",\n" +
			"\"name\": \"Keyword\",\n" +
			"\"fields\": [{\n" +
			"\"name\": \"keyword\", \"type\": { \"type\": \"enum\",\n" +
			"    \"name\": \"keywords\", \"symbols\": [\"nonkeyword\", \"new\"]}}]\n" +
			"}";

	// '$' is added by Avro when generating a Java enum class whose value is Java reserved keywords
	public enum KeyWords { nonkeyword, new$ }

	public static class Keyword {

		public KeyWords keyword;

		protected Keyword() {}

		public Keyword(KeyWords w) {
			keyword = w;
		}
	}

	public void testEnumWithJavaReservedKeywordsFailed() throws IOException {
		String json = "{\"keyword\":\"new\"}";
		Keyword k = OBJECT_MAPPER.treeToValue(OBJECT_MAPPER.readTree(json), Keyword.class);
	}

	public void testEnumWithJavaReservedKeywordsSucceeded() throws IOException {
		String json = "{\"keyword\":\"new$\"}";
		Keyword k = OBJECT_MAPPER.treeToValue(OBJECT_MAPPER.readTree(json), Keyword.class);
	}
}
