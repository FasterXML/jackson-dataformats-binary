package com.fasterxml.jackson.dataformat.avro.schemaev;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RecordEvolutionTest extends AvroTestBase
{
	static String SCHEMA_V1 = "{\n"
        + " \"type\": \"record\",\n"
        + " \"name\": \"User\",\n"
        + " \"fields\": [\n"
        + "     {\n"
        + "        \"name\": \"name\",\n"
        + "        \"type\": [\"string\", \"null\"],\n"
        + "        \"default\": null\n"
        + "    },\n"
        + "     {\n"
        + "        \"name\": \"preferences\", \n"
        + "        \"type\": {\n"
        + "            \"type\": \"map\",\n"
        + "            \"values\": {\n"
        + "                \"type\": \"array\",\n"
        + "                \"items\": \"string\"\n"
        + "            }\n"
        + "        }\n"
        + "    }\n"
        + " ]\n"
        + "}";

	static String SCHEMA_V2 = "{\n"
        + " \"type\": \"record\",\n"
        + " \"name\": \"User\",\n"
        + " \"fields\": [\n"
        + "     {\n"
        + "        \"name\": \"fullName\",\n"
        + "        \"type\": [\"string\", \"null\"],\n"
        + "        \"default\": null,\n"
        + "        \"aliases\": [\"name\"]\n"
        + "    },\n"
        + "     {\n"
        + "        \"name\": \"preferences\", \n"
        + "        \"type\": {\n"
        + "            \"type\": \"map\",\n"
        + "            \"values\": {\n"
        + "                \"type\": \"array\",\n"
        + "                \"items\": \"string\"\n"
        + "            }\n"
        + "        }\n"
        + "    }\n"
        + " ]\n"
        + "}";

	static class UserV1 {
		public String name;
		public Map<String, List<String>> preferences;

		@JsonCreator
		public UserV1(final String name, final Map<String, List<String>> preferences) {
			this.name = name;
			this.preferences = preferences;
		}

		public boolean equals(final Object object) {
			if (this == object) {
				return true;
			}

			if (!(object instanceof UserV1)) {
				return false;
			}

			final UserV1 user = (UserV1) object;

			return name.equals(user.name) &&
				preferences.equals(user.preferences);
		}

		public int hashCode() {
			return Objects.hash(name, preferences);
		}

		@Override
		public String toString() {
			return "UserV1{" +
				"name='" + name + '\'' +
				", preferences=" + preferences +
				'}';
		}
	}

	static class UserV2 {
		public String fullName;
		public Map<String, List<String>> preferences;

		public UserV2(
			@JsonProperty("fullName") final String fullName,
			@JsonProperty("preferences") final Map<String, List<String>> preferences
		) {
			this.fullName = fullName;
			this.preferences = preferences;
		}

		public boolean equals(final Object object) {
			if (this == object) {
				return true;
			}

			if (!(object instanceof UserV2)) {
				return false;
			}

			final UserV2 user = (UserV2) object;

			return fullName.equals(user.fullName) && preferences.equals(user.preferences);
		}

		public int hashCode() {
			return Objects.hash(fullName, preferences);
		}

		@Override
		public String toString() {
			return "UserV2{" +
				"fullName='" + fullName + '\'' +
				", preferences=" + preferences +
				'}';
		}
	}

	private final AvroMapper MAPPER = getMapper();

	public void testEvolutionInvolvingComplexRecords() throws Exception
	{
		final AvroSchema schemaV1 = MAPPER.schemaFrom(SCHEMA_V1);
		final AvroSchema schemaV2 = MAPPER.schemaFrom(SCHEMA_V2);
		final AvroSchema combinedSchema = schemaV1.withReaderSchema(schemaV2);

		final Map<String, List<String>> preferences = new HashMap<>();
		final List<String> list = new ArrayList<>();
		list.add("yes");

		preferences.put("jackson", list);

		final UserV1 userV1 = new UserV1("foo", preferences);

		final byte[] avro = MAPPER.writer(schemaV1).writeValueAsBytes(userV1);

		final UserV2 userV2 = MAPPER.readerFor(UserV2.class)
			.with(combinedSchema)
			.readValue(avro);

		assertEquals(userV2.fullName, userV1.name);
		assertEquals(userV2.preferences, userV1.preferences);
	}
}
