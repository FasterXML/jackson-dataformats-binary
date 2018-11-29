package com.fasterxml.jackson.dataformat.protobuf.schema;

import static org.junit.Assert.assertArrayEquals;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufTestBase;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufMessage;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;
import com.fasterxml.jackson.dataformat.protobuf.schemagen.ProtobufSchemaGenerator;

public class SchemaGenTest extends ProtobufTestBase
{
	public static class WithNestedClass {
		@JsonProperty(required = true)
		public String name;
		public NestedClass[] nestedClasses;
		public NestedEnum nestedEnum;

		public static class NestedClass {
			public int id;
		}

		public static enum NestedEnum {
			A, B, C;
		}
	}

	public static class WithIndexAnnotation {
		@JsonProperty(required = true, index = 1)
		public float f;

		@JsonProperty(index = 4)
		public boolean b;

		@JsonProperty(index = 3)
		public ByteBuffer bb;

		@JsonProperty(index = 2)
		public double d;
	}

	public static class RootType {
		public String name;

		public int value;

		public List<String> other;
	}

	public static class Employee {
		@JsonProperty(required = true)
		public String name;

		@JsonProperty(required = true)
		public int age;

		public String[] emails;

		public Employee boss;
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

	private final ProtobufMapper MAPPER = newObjectMapper();

	public void testWithNestedClass() throws Exception
	{
		ProtobufSchema schemaWrapper = MAPPER.generateSchemaFor(WithNestedClass.class);

		assertNotNull(schemaWrapper);

		// System.out.println(schemaWrapper.getSource().toString());
	}

	public void testWithIndexAnnotation() throws Exception
	{
         ProtobufSchema schemaWrapper = MAPPER.generateSchemaFor(WithIndexAnnotation.class);

		assertNotNull(schemaWrapper);

		// System.out.println(schemaWrapper.getSource().toString());

		ProtobufMessage pMessage = schemaWrapper.getRootType();
		assertEquals("f", pMessage.field(1).name);
		assertEquals("d", pMessage.field(2).name);
		assertEquals("bb", pMessage.field(3).name);
		assertEquals("b", pMessage.field(4).name);
	}

	public void testSelfRefPojoGenProtobufSchema() throws Exception {
	    ProtobufMapper mapper = new ProtobufMapper();
		ProtobufSchemaGenerator gen = new ProtobufSchemaGenerator();
		mapper.acceptJsonFormatVisitor(Employee.class, gen);
		ProtobufSchema schemaWrapper = mapper.generateSchemaFor(Employee.class);

		assertNotNull(schemaWrapper);

		ProtobufMessage pMessage = schemaWrapper.getRootType();
		assertTrue(pMessage.field("name").required);
		assertFalse(pMessage.field("boss").required);

		String protoFile = schemaWrapper.getSource().toString();
		// System.out.println(protoFile);

		Employee empl = buildEmployee();

		byte[] byteMsg = mapper.writer(schemaWrapper).writeValueAsBytes(empl);
		// System.out.println(byteMsg);
		ProtobufSchema schema = ProtobufSchemaLoader.std.parse(protoFile);
		Employee newEmpl = mapper.readerFor(Employee.class).with(schema).readValue(byteMsg);

		// System.out.println(newEmpl);
		assertEquals(empl.name, newEmpl.name);
		assertEquals(empl.age, newEmpl.age);
		assertArrayEquals(empl.emails, newEmpl.emails);
		assertEquals(empl.boss, newEmpl.boss);
	}

	public void testComplexPojoGenProtobufSchema() throws Exception {
		ObjectMapper mapper = new ProtobufMapper();
		ProtobufSchemaGenerator gen = new ProtobufSchemaGenerator();
		mapper.acceptJsonFormatVisitor(MediaItem.class, gen);
		ProtobufSchema schemaWrapper = gen.getGeneratedSchema();
		assertNotNull(schemaWrapper);

		String protoFile = schemaWrapper.getSource().toString();
		// System.out.println(protoFile);

		MediaItem mediaItem = MediaItem.buildItem();

		byte[] byteMsg = mapper.writerFor(MediaItem.class).with(schemaWrapper).writeValueAsBytes(mediaItem);
		// System.out.println(byteMsg);
		ProtobufSchema schema = ProtobufSchemaLoader.std.parse(protoFile);
		MediaItem deserMediaItem = mapper.readerFor(MediaItem.class).with(schema).readValue(byteMsg);

		// System.out.println(deserMediaItem);
		assertEquals(mediaItem, deserMediaItem);
	}

	public void testSimplePojoGenProtobufSchema() throws Exception {
		ObjectMapper mapper = new ProtobufMapper();
		ProtobufSchemaGenerator gen = new ProtobufSchemaGenerator();
		mapper.acceptJsonFormatVisitor(RootType.class, gen);
		ProtobufSchema schemaWrapper = gen.getGeneratedSchema();

		assertNotNull(schemaWrapper);

		String protoFile = schemaWrapper.getSource().toString();
		// System.out.println(protoFile);

		RootType rType = buildRootType();

		byte[] msg = mapper.writerFor(RootType.class).with(schemaWrapper).writeValueAsBytes(rType);
		// System.out.println(msg);
		ProtobufSchema schema = ProtobufSchemaLoader.std.parse(protoFile);
		RootType parsedRootType = mapper.readerFor(RootType.class).with(schema).readValue(msg);

		// System.out.println(parsedRootType);
		assertEquals(rType.name, parsedRootType.name);
		assertEquals(rType.value, parsedRootType.value);
		assertEquals(rType.other, parsedRootType.other);
	}

     protected RootType buildRootType() {
         RootType rType = new RootType();
         rType.name = "rTpye";
         rType.value = 100;
         rType.other = new ArrayList<String>();
         rType.other.add("12345");
         rType.other.add("abcdefg");
         return rType;
    }

    protected Employee buildEmployee() {
         Employee empl = new Employee();
         empl.name = "Bobbee";
         empl.age = 39;
         empl.emails = new String[] { "bob@aol.com", "bobby@gmail.com" };
         empl.boss = null;
         return empl;
    }
}
