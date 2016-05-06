package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.util.Map;

import org.apache.avro.Schema;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator;

public class NestedMapTest extends AvroTestBase
{
	public static class Nester {
		@JsonProperty
		public Map<String,Map<String,Integer>> nested;
	}

	public void testSerialization() throws IOException
	{
		Nester fromJson = new ObjectMapper().readValue(
				"{\"nested\": {\"map\":{\"value\":1}}}"
				, Nester.class);
		
		AvroMapper mapper = new AvroMapper();
		//Generate schema from class
		AvroSchemaGenerator gen = new AvroSchemaGenerator();
		mapper.acceptJsonFormatVisitor(Nester.class, gen);
		Schema schema = gen.getGeneratedSchema().getAvroSchema(); 

		//Serialize
		byte[] avroData =  mapper.writer(new AvroSchema(schema))
				.writeValueAsBytes(fromJson);

		//Deserialize
		Nester nester = mapper.readerFor(Nester.class)
				   .with(new AvroSchema(schema))
				   .readValue(avroData);
		int val = nester.nested.get("map").get("value");
		assertEquals(1, val);
		
	}
}
