package com.fasterxml.jackson.dataformat.protobuf;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.core.JsonParser.NumberTypeFP;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

public class ReadSimpleTest extends ProtobufTestBase
{
    final protected static String PROTOC_STRINGS =
            "message Strings {\n"
            +" repeated string values = 3;\n"
            +"}\n"
    ;

    final protected static String PROTOC_STRINGS_PACKED =
            "message Strings {\n"
            +" repeated string values = 2 [packed=true];\n"
            +"}\n"
    ;

    final protected static String PROTOC_NAMED_STRINGS =
            "message NamedStrings {\n"
            +" required string name = 2;\n"
            +" repeated string values = 7;\n"
            +"}\n"
    ;

    static class Strings {
        public String[] values;

        public Strings() { }
        public Strings(String... v) { values = v; }
    }

    static class NamedStrings {
        public String name;
        public String[] values;

        public NamedStrings() { }
        public NamedStrings(String n, String... v) {
            name = n;
            values = v;
        }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = newObjectMapper();

    public void testReadPointIntAsPOJO() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_BOX, "Point");
        final ObjectWriter w = MAPPER.writerFor(Point.class)
                .with(schema);
        Point input = new Point(151, -444);
        byte[] bytes = w.writeValueAsBytes(input);

        // 6 bytes: 1 byte tags, 2 byte values
        assertEquals(6, bytes.length);

        // but more importantly, try to parse
        Point result = MAPPER.readerFor(Point.class).with(schema).readValue(bytes);
        assertNotNull(result);
        assertEquals(input.x, result.x);
        assertEquals(input.y, result.y);
    }

    public void testReadPointIntStreaming() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_BOX, "Point");
        final ObjectWriter w = MAPPER.writerFor(Point.class)
                .with(schema);
        Point input = new Point(151, -444);
        byte[] bytes = w.writeValueAsBytes(input);
        assertEquals(6, bytes.length);

        // actually let's also try via streaming parser
        JsonParser p = MAPPER.getFactory().createParser(bytes);
        p.setSchema(schema);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertNull(p.currentName());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("x", p.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals(NumberType.INT, p.getNumberType());
        assertEquals("x", p.currentName());
        assertEquals(NumberTypeFP.UNKNOWN, p.getNumberTypeFP());
        assertEquals(input.x, p.getIntValue());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("y", p.currentName());
        assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
        assertEquals("y", p.currentName());
        assertEquals(input.y, p.getIntValue());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.currentName());
        p.close();
        assertNull(p.currentName());
    }

    public void testReadPointLong() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_POINT_L);
        final ObjectWriter w = MAPPER.writerFor(Point.class)
                .with(schema);
        Point input = new Point(Integer.MAX_VALUE, Integer.MIN_VALUE);
        byte[] bytes = w.writeValueAsBytes(input);
        assertNotNull(bytes);

        assertEquals(12, bytes.length);

        // but more importantly, try to parse
        Point result = MAPPER.readerFor(Point.class).with(schema).readValue(bytes);
        assertNotNull(result);
        assertEquals(input.x, result.x);
        assertEquals(input.y, result.y);

        // actually let's also try via streaming parser
        try (JsonParser p = MAPPER.getFactory().createParser(bytes)) {
            p.setSchema(schema);
            assertNull(p.currentName());
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertNull(p.currentName());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("x", p.currentName());
            assertEquals("x", p.getText());
            assertEquals("x", p.getValueAsString());
            assertEquals("x", p.getValueAsString("y"));
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(NumberType.LONG, p.getNumberType());
            assertEquals(NumberTypeFP.UNKNOWN, p.getNumberTypeFP());
            assertEquals(input.x, p.getIntValue());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("y", p.currentName());
            assertEquals("y", p.getText());
            assertEquals("y", p.getValueAsString());
            assertEquals("y", p.getValueAsString("abc"));
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(input.y, p.getIntValue());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.currentName());
        }
    }

    public void testReadName() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_NAME);
        final ObjectWriter w = MAPPER.writerFor(Name.class)
                .with(schema);
        // make sure to use at least one non-ascii char in there:
        Name input = new Name("Billy", "Baco\u00F1");

        byte[] bytes = w.writeValueAsBytes(input);
        assertNotNull(bytes);

        assertEquals(15, bytes.length);

        Name result = MAPPER.readerFor(Name.class).with(schema).readValue(bytes);
        assertNotNull(result);
        assertEquals(input.first, result.first);
        assertEquals(input.last, result.last);
    }

    public void testReadBox() throws Exception
    {
        final ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_BOX);
        final ObjectWriter w = MAPPER.writerFor(Box.class)
                .with(schema);
        Point topLeft = new Point(100, 150);
        Point bottomRight = new Point(500, 1090);
        Box input = new Box(topLeft, bottomRight);

        byte[] bytes = w.writeValueAsBytes(input);
        assertNotNull(bytes);

        assertEquals(15, bytes.length);

        Box result = MAPPER.readerFor(Box.class).with(schema).readValue(bytes);
        assertNotNull(result);
        assertNotNull(result.topLeft);
        assertNotNull(result.bottomRight);
        assertEquals(input.topLeft, result.topLeft);
        assertEquals(input.bottomRight, result.bottomRight);

        // But let's try streaming too:
        // actually let's also try via streaming parser
        try (JsonParser p = MAPPER.getFactory().createParser(bytes)) {
            p.setSchema(schema);
            assertNull(p.currentName());
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertNull(p.currentName());

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("topLeft", p.currentName());
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("topLeft", p.currentName());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("x", p.currentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals("x", p.currentName());
            assertEquals(input.topLeft.x, p.getIntValue());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("y", p.currentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals("y", p.currentName());
            assertEquals(input.topLeft.y, p.getIntValue());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertEquals("topLeft", p.currentName());

            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("bottomRight", p.currentName());
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertEquals("bottomRight", p.currentName());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("x", p.currentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals("x", p.currentName());
            assertEquals(input.bottomRight.x, p.getIntValue());
            assertToken(JsonToken.FIELD_NAME, p.nextToken());
            assertEquals("y", p.currentName());
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals("y", p.currentName());
            assertEquals(input.bottomRight.y, p.getIntValue());
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertEquals("bottomRight", p.currentName());
            
            assertToken(JsonToken.END_OBJECT, p.nextToken());
            assertNull(p.currentName());
        }
    }

    public void testStringArraySimple() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_STRINGS);
        final ObjectWriter w = MAPPER.writerFor(Strings.class)
                .with(schema);
        Strings input = new Strings("Dogs", "like", "Baco\u00F1");
        byte[] bytes = w.writeValueAsBytes(input);
        assertNotNull(bytes);
        assertEquals(20, bytes.length);

        Strings result = MAPPER.readerFor(Strings.class).with(schema).readValue(bytes);
        assertNotNull(result);
        assertNotNull(result.values);
        assertEquals(input.values.length, result.values.length);
        for (int i = 0; i < result.values.length; ++i) {
            assertEquals(input.values[i], result.values[i]);
        }

        // and also verify via streaming
        JsonParser p = MAPPER.getFactory().createParser(bytes);
        p.setSchema(schema);
        assertToken(JsonToken.START_OBJECT, p.nextToken());

        assertEquals("/", p.getParsingContext().toString());

        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("values", p.currentName());

        // 23-May-2016, tatu: Not working properly yet:
//        assertEquals("{values}", p.getParsingContext().toString());

        assertToken(JsonToken.START_ARRAY, p.nextToken());

        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(input.values[0], p.getText());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(input.values[1], p.getText());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(input.values[2], p.getText());

        StringWriter strw = new StringWriter();
        assertEquals(input.values[2].length(), p.getText(strw));
        assertEquals(input.values[2], strw.toString());

        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        p.close();

        // and, ditto, but skipping
        p = MAPPER.getFactory().createParser(bytes);
        p.setSchema(schema);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertToken(JsonToken.START_ARRAY, p.nextToken());

        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        // but only skip first couple, not last
        assertEquals(input.values[2], p.getText());

        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        p.close();
        assertNull(p.nextToken());
    }

    public void testStringArrayPacked() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_STRINGS_PACKED);
        final ObjectWriter w = MAPPER.writerFor(Strings.class)
                .with(schema);
        Strings input = new Strings("Dogs", "like", "Baco\u00F1");
        byte[] bytes = w.writeValueAsBytes(input);
        assertNotNull(bytes);
        // one byte less, due to length prefix
        assertEquals(19, bytes.length);

        Strings result = MAPPER.readerFor(Strings.class).with(schema).readValue(bytes);
        assertNotNull(result);
        assertNotNull(result.values);
        assertEquals(input.values.length, result.values.length);
        for (int i = 0; i < result.values.length; ++i) {
            assertEquals(input.values[i], result.values[i]);
        }
    }

    public void testStringArrayWithName() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_NAMED_STRINGS);
        final ObjectWriter w = MAPPER.writerFor(NamedStrings.class)
                .with(schema);
        NamedStrings input = new NamedStrings("abc123", "a", "b", "", "d");
        byte[] bytes = w.writeValueAsBytes(input);
        assertNotNull(bytes);
        assertEquals(19, bytes.length);

        NamedStrings result = MAPPER.readerFor(NamedStrings.class).with(schema).readValue(bytes);
        assertNotNull(result);
        assertEquals(input.name, result.name);
        assertNotNull(result.values);
        assertEquals(input.values.length, result.values.length);
        for (int i = 0; i < result.values.length; ++i) {
            assertEquals(input.values[i], result.values[i]);
        }

        // and also verify via streaming
        JsonParser p = MAPPER.getFactory().createParser(bytes);
        p.setSchema(schema);
        assertToken(JsonToken.START_OBJECT, p.nextToken());

        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("name", p.currentName());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(input.name, p.getText());

        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("values", p.currentName());
        assertToken(JsonToken.START_ARRAY, p.nextToken());

        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertFalse(p.hasTextCharacters());
        assertEquals(input.values[0], p.getText());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(input.values[1], p.getText());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(input.values[2], p.getText());
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(input.values[3], p.getText());

        assertToken(JsonToken.END_ARRAY, p.nextToken());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        p.close();

        // also, for fun: partial read
        p = MAPPER.getFactory().createParser(bytes);
        p.setSchema(schema);
        assertToken(JsonToken.START_OBJECT, p.nextToken());

        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("name", p.currentName());
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int count = p.releaseBuffered(b);
        assertEquals(count, b.size());
        assertEquals(18, count);

        p.close();
    }

    public void testSearchMessage() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_SEARCH_REQUEST);
        final ObjectWriter w = MAPPER.writerFor(SearchRequest.class)
                .with(schema);
        SearchRequest input = new SearchRequest();
        input.corpus = Corpus.WEB;
        input.page_number = 3;
        input.result_per_page = 200;
        input.query = "get all";

        byte[] bytes = w.writeValueAsBytes(input);
        assertNotNull(bytes);
        assertEquals(16, bytes.length);

        SearchRequest result = MAPPER.readerFor(SearchRequest.class).with(schema).readValue(bytes);
        assertNotNull(result);

        assertEquals(input.page_number, result.page_number);
        assertEquals(input.result_per_page, result.result_per_page);
        assertEquals(input.query, result.query);
        assertEquals(input.corpus, result.corpus);
    }

    public void testSkipUnknown() throws Exception
    {
        ProtobufSchema pointSchema = ProtobufSchemaLoader.std.parse(PROTOC_POINT);
        ProtobufSchema point3Schema = ProtobufSchemaLoader.std.parse(PROTOC_POINT3);

        // Important: write Point3, read regular Point
        ProtobufMapper mapper = newObjectMapper();
        mapper.enable(JsonParser.Feature.IGNORE_UNDEFINED);

        final Point3 input = new Point3(1, 2, 3);
        byte[] stuff = mapper.writerFor(Point3.class)
                .with(point3Schema)
                .writeValueAsBytes(input);

        Point result = mapper.readerFor(Point.class).with(pointSchema).readValue(stuff);
        assertNotNull(result);
        assertEquals(input.x, result.x);
        assertEquals(input.y, result.y);
    }

    public void testStringArraySimpleLowLimit() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_STRINGS);
        ProtobufFactory protobufFactory = ProtobufFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(1).build())
                .build();
        ProtobufMapper protobufMapper = ProtobufMapper.builder(protobufFactory).build();
        final ObjectWriter w = protobufMapper.writerFor(Strings.class)
                .with(schema);
        Strings input = new Strings("Dogs", "like", "Baco\u00F1");
        byte[] bytes = w.writeValueAsBytes(input);
        assertNotNull(bytes);
        assertEquals(20, bytes.length);

        try {
            protobufMapper.readerFor(Strings.class).with(schema).readValue(bytes);
            fail("Expected DatabindException");
        } catch (DatabindException jme) {
            String message = jme.getMessage();
            assertTrue("unexpected message: " + message,
                    message.startsWith("String value length (4) exceeds the maximum allowed"));
        }
    }
}
