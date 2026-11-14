package tools.jackson.dataformat.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.message.BinaryMessageEncoder;
import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.ObjectReader;
import tools.jackson.dataformat.avro.testsupport.ThrottledInputStream;

import static org.junit.jupiter.api.Assertions.*;

// [dataformats-binary#343]: reading Avro "Single Object Encoding"
public class SingleObjectEncoding343Test extends AvroTestBase
{
    private final static String SCHEMA_JSON = "{"
        + "\"type\": \"record\","
        + "\"name\": \"SampleRecord\","
        + "\"fields\": ["
        + "  {\"name\": \"name\", \"type\": \"string\"}"
        + "]}";

    private final static String OTHER_SCHEMA_JSON = "{"
        + "\"type\": \"record\","
        + "\"name\": \"OtherRecord\","
        + "\"fields\": ["
        + "  {\"name\": \"name\", \"type\": \"string\"}"
        + "]}";

    private final Schema APACHE_SCHEMA = new Schema.Parser().parse(SCHEMA_JSON);

    private final AvroSchema JACKSON_SCHEMA = new AvroSchema(APACHE_SCHEMA);

    private final AvroMapper[] MAPPERS = new AvroMapper[] {
            newMapper(),
            newApacheMapper(),
            new AvroMapper(AvroFactory.builderWithApacheDecoder()
                    .disable(AvroReadFeature.AVRO_BUFFERING).build())
    };

    @Test
    public void testReadSingleObjectEncoding() throws Exception
    {
        // Encode using Apache Avro's Single Object Encoding (BinaryMessageEncoder),
        // which is what SpecificRecord.toByteBuffer() uses in Apache Avro 1.8+.
        byte[] bytes = encodeSingleObject(APACHE_SCHEMA, "apm");
        assertEquals((byte) 0xC3, bytes[0], "First magic byte should be 0xC3");
        assertEquals((byte) 0x01, bytes[1], "Second magic byte should be 0x01");

        for (AvroMapper mapper : MAPPERS) {
            JsonNode result = singleObjectReader(mapper).readTree(bytes);
            assertNotNull(result);
            assertEquals("apm", result.get("name").asString());
        }
    }

    @Test
    public void testReadSequenceOfSingleObjectValues() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(encodeSingleObject(APACHE_SCHEMA, "a"));
        out.write(encodeSingleObject(APACHE_SCHEMA, "bc"));
        byte[] bytes = out.toByteArray();

        for (AvroMapper mapper : MAPPERS) {
            try (MappingIterator<JsonNode> it = singleObjectReader(mapper)
                    .forType(JsonNode.class).readValues(bytes)) {
                List<JsonNode> values = it.readAll();
                assertEquals(2, values.size());
                assertEquals("a", values.get(0).get("name").asString());
                assertEquals("bc", values.get(1).get("name").asString());
            }
            try (MappingIterator<JsonNode> it = singleObjectReader(mapper)
                    .forType(JsonNode.class).readValues(new ThrottledInputStream(bytes, 3))) {
                List<JsonNode> values = it.readAll();
                assertEquals(2, values.size());
                assertEquals("a", values.get(0).get("name").asString());
                assertEquals("bc", values.get(1).get("name").asString());
            }
        }
    }

    @Test
    public void testReadSingleObjectEncodingFromStream() throws Exception
    {
        byte[] bytes = encodeSingleObject(APACHE_SCHEMA, "apm");

        for (AvroMapper mapper : MAPPERS) {
            JsonNode result = singleObjectReader(mapper)
                    .readTree(new ByteArrayInputStream(bytes));
            assertEquals("apm", result.get("name").asString());
        }
    }

    @Test
    public void testReadWithReaderSchema() throws Exception
    {
        Schema readerSchema = new Schema.Parser().parse("{"
                + "\"type\": \"record\","
                + "\"name\": \"SampleRecord\","
                + "\"fields\": ["
                + "  {\"name\": \"name\", \"type\": \"string\"},"
                + "  {\"name\": \"count\", \"type\": \"int\", \"default\": 7}"
                + "]}");
        AvroSchema resolving = JACKSON_SCHEMA.withReaderSchema(new AvroSchema(readerSchema));
        byte[] bytes = encodeSingleObject(APACHE_SCHEMA, "apm");

        for (AvroMapper mapper : MAPPERS) {
            JsonNode result = mapper.reader(resolving)
                    .with(AvroReadFeature.READ_SINGLE_OBJECT_ENCODING)
                    .readTree(bytes);
            assertEquals("apm", result.get("name").asString());
            assertEquals(7, result.get("count").asInt());
        }
    }

    @Test
    public void testReadWithReaderSchemaAliases() throws Exception
    {
        Schema writerSchema = new Schema.Parser().parse("{"
                + "\"type\": \"record\","
                + "\"name\": \"OldRecord\","
                + "\"fields\": ["
                + "  {\"name\": \"oldName\", \"type\": \"string\"}"
                + "]}");
        Schema readerSchema = new Schema.Parser().parse("{"
                + "\"type\": \"record\","
                + "\"name\": \"NewRecord\", \"aliases\": [\"OldRecord\"],"
                + "\"fields\": ["
                + "  {\"name\": \"newName\", \"type\": \"string\", \"aliases\": [\"oldName\"]}"
                + "]}");
        Schema otherReaderSchema = new Schema.Parser().parse("{"
                + "\"type\": \"record\","
                + "\"name\": \"NewRecord\", \"aliases\": [\"OldRecord\"],"
                + "\"fields\": ["
                + "  {\"name\": \"newName\", \"type\": \"string\", \"aliases\": [\"oldName\"]},"
                + "  {\"name\": \"count\", \"type\": \"int\", \"default\": 7}"
                + "]}");
        GenericRecord record = new GenericData.Record(writerSchema);
        record.put("oldName", "apm");
        byte[] bytes = toByteArray(new BinaryMessageEncoder<GenericRecord>(
                GenericData.get(), writerSchema).encode(record));

        final AvroSchema reader = new AvroSchema(readerSchema);
        final AvroSchema otherReader = new AvroSchema(otherReaderSchema);
        final AvroSchema[] schemas = new AvroSchema[] {
                new AvroSchema(writerSchema).withReaderSchema(reader),
                new AvroSchema(writerSchema).withUnsafeReaderSchema(reader),
                new AvroSchema(writerSchema).withReaderSchema(reader).withReaderSchema(otherReader),
                new AvroSchema(writerSchema).withReaderSchema(reader).withUnsafeReaderSchema(otherReader)
        };
        final long fingerprint = new AvroSchema(writerSchema).getWriterFingerprint();
        for (AvroSchema schema : schemas) {
            assertEquals(fingerprint, schema.getWriterFingerprint());
        }
        final AvroSchema renamed = new AvroSchema(new Schema.Parser().parse("{"
                + "\"type\": \"record\","
                + "\"name\": \"NewRecord\","
                + "\"fields\": ["
                + "  {\"name\": \"newName\", \"type\": \"string\"}"
                + "]}")).withReaderSchema(otherReader);
        assertNotEquals(fingerprint, renamed.getWriterFingerprint());
        assertNotEquals(new AvroSchema(writerSchema).withReaderSchema(otherReader), renamed);
        for (AvroMapper mapper : MAPPERS) {
            for (AvroSchema schema : schemas) {
                JsonNode result = mapper.reader(schema)
                        .with(AvroReadFeature.READ_SINGLE_OBJECT_ENCODING)
                        .readTree(bytes);
                assertEquals("apm", result.get("newName").asString());
            }
            JsonNode result = mapper.reader(schemas[2])
                    .with(AvroReadFeature.READ_SINGLE_OBJECT_ENCODING)
                    .readTree(bytes);
            assertEquals(7, result.get("count").asInt());
        }
    }

    @Test
    public void testReadEmptyRecords() throws Exception
    {
        Schema emptySchema = new Schema.Parser().parse(
                "{\"type\": \"record\", \"name\": \"Empty\", \"fields\": []}");
        BinaryMessageEncoder<GenericRecord> encoder =
                new BinaryMessageEncoder<>(GenericData.get(), emptySchema);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(toByteArray(encoder.encode(new GenericData.Record(emptySchema))));
        out.write(toByteArray(encoder.encode(new GenericData.Record(emptySchema))));
        byte[] bytes = out.toByteArray();
        assertEquals(20, bytes.length);

        for (AvroMapper mapper : MAPPERS) {
            try (MappingIterator<JsonNode> it = mapper.reader(new AvroSchema(emptySchema))
                    .with(AvroReadFeature.READ_SINGLE_OBJECT_ENCODING)
                    .forType(JsonNode.class).readValues(bytes)) {
                assertEquals(0, it.nextValue().size());
                assertEquals(0, it.nextValue().size());
                assertFalse(it.hasNextValue());
            }
        }
    }

    @Test
    public void testFailForTruncatedHeader() throws Exception
    {
        byte[] bytes = Arrays.copyOf(encodeSingleObject(APACHE_SCHEMA, "apm"), 6);

        for (AvroMapper mapper : MAPPERS) {
            JacksonException e = assertThrows(JacksonException.class,
                    () -> singleObjectReader(mapper).readTree(bytes));
            // Apache decoder reports end-of-input as `EOFException`, wrapped
            if (!(e.getCause() instanceof EOFException)) {
                verifyException(e, "end-of-input");
            }
        }
    }

    @Test
    public void testFailForMissingHeader() throws Exception
    {
        byte[] bytes = encodeBinary(APACHE_SCHEMA, "no header in this one");
        byte[] otherVersion = encodeSingleObject(APACHE_SCHEMA, "apm");
        otherVersion[1] = 0x02;
        byte[] otherMarker = encodeSingleObject(APACHE_SCHEMA, "apm");
        otherMarker[0] = (byte) 0xC2;

        for (AvroMapper mapper : MAPPERS) {
            StreamReadException e = assertThrows(StreamReadException.class,
                    () -> singleObjectReader(mapper).readTree(bytes));
            verifyException(e, "Invalid Avro Single Object Encoding header");

            e = assertThrows(StreamReadException.class,
                    () -> singleObjectReader(mapper).readTree(otherVersion));
            verifyException(e, "Invalid Avro Single Object Encoding header");
            verifyException(e, "got 0xC3 0x02");

            e = assertThrows(StreamReadException.class,
                    () -> singleObjectReader(mapper).readTree(otherMarker));
            verifyException(e, "Invalid Avro Single Object Encoding header");
            verifyException(e, "got 0xC2 0x01");
        }
    }

    @Test
    public void testFailForFingerprintMismatch() throws Exception
    {
        Schema otherSchema = new Schema.Parser().parse(OTHER_SCHEMA_JSON);
        byte[] bytes = encodeSingleObject(otherSchema, "apm");
        String fingerprints = String.format(
                "value has 0x%016X, writer schema used for reading has 0x%016X",
                SchemaNormalization.parsingFingerprint64(otherSchema),
                SchemaNormalization.parsingFingerprint64(APACHE_SCHEMA));

        for (AvroMapper mapper : MAPPERS) {
            StreamReadException e = assertThrows(StreamReadException.class,
                    () -> singleObjectReader(mapper).readTree(bytes));
            verifyException(e, "schema fingerprint mismatch");
            verifyException(e, fingerprints);
        }
    }

    @Test
    public void testFailForValueWithoutHeaderInSequence() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(encodeSingleObject(APACHE_SCHEMA, "a"));
        out.write(encodeBinary(APACHE_SCHEMA, "no header in this one"));
        byte[] bytes = out.toByteArray();

        for (AvroMapper mapper : MAPPERS) {
            try (MappingIterator<JsonNode> it = singleObjectReader(mapper)
                    .forType(JsonNode.class).readValues(bytes)) {
                assertEquals("a", it.nextValue().get("name").asString());
                StreamReadException e = assertThrows(StreamReadException.class,
                        () -> it.nextValue());
                verifyException(e, "Invalid Avro Single Object Encoding header");
            }
        }
    }

    @Test
    public void testHeadersCountForMaxDocumentLength() throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < 3; ++i) {
            out.write(encodeSingleObject(APACHE_SCHEMA, "abc"));
        }
        byte[] bytes = out.toByteArray();
        assertEquals(42, bytes.length);

        for (int maxLength : new int[] { 41, 42 }) {
            StreamReadConstraints constraints = StreamReadConstraints.builder()
                    .maxDocumentLength(maxLength).build();
            for (AvroFactoryBuilder b : new AvroFactoryBuilder[] {
                    AvroFactory.builderWithNativeDecoder(),
                    AvroFactory.builderWithApacheDecoder(),
                    AvroFactory.builderWithApacheDecoder().disable(AvroReadFeature.AVRO_BUFFERING) }) {
                ObjectReader r = singleObjectReader(new AvroMapper(
                        b.streamReadConstraints(constraints).build()))
                        .forType(JsonNode.class);
                try (MappingIterator<JsonNode> it = r.readValues(new ByteArrayInputStream(bytes))) {
                    if (maxLength < bytes.length) {
                        assertThrows(StreamConstraintsException.class, () -> it.readAll());
                    } else {
                        assertEquals(3, it.readAll().size());
                    }
                }
            }
        }
    }

    @Test
    public void testWriterFingerprintSameAsApache() throws Exception
    {
        Schema schema = new Schema.Parser().parse(a2q("{"
                +"'type':'record','name':'Order','namespace':'com.example.orders',"
                +"'doc':'An order','aliases':['OldOrder'],'custom':'value',"
                +"'fields':["
                +" {'name':'id','type':{'type':'string','logicalType':'uuid'},"
                +"  'default':'00000000-0000-0000-0000-000000000000','doc':'Id','aliases':['key']},"
                +" {'name':'created','type':{'type':'long','logicalType':'timestamp-millis'},'default':0},"
                +" {'name':'amount','type':{'type':'bytes','logicalType':'decimal','precision':9,'scale':2},"
                +"  'default':''},"
                +" {'name':'status','type':{'type':'enum','name':'Status','namespace':'com.example.common',"
                +"  'symbols':['NEW','DONE'],'default':'NEW'},'default':'DONE','order':'descending'},"
                +" {'name':'code','type':{'type':'fixed','name':'Code','size':2},'default':'ab'},"
                +" {'name':'tags','type':{'type':'map',"
                +"  'values':{'type':'array','items':'com.example.common.Status'}},'default':{}},"
                +" {'name':'next','type':['null','Order'],'default':null},"
                +" {'name':'line','type':{'type':'record','name':'Line',"
                +"  'fields':[{'name':'qty','type':'int','default':1}]},'default':{'qty':2}}"
                +"]}"));
        byte[] bytes = toByteArray(new BinaryMessageEncoder<GenericRecord>(GenericData.get(), schema)
                .encode(new GenericRecordBuilder(schema).build()));
        long fingerprint = 0L;
        for (int i = 9; i >= 2; --i) {
            fingerprint = (fingerprint << 8) | (bytes[i] & 0xFFL);
        }
        assertEquals(fingerprint, new AvroSchema(schema).getWriterFingerprint());

        for (AvroMapper mapper : MAPPERS) {
            JsonNode result = mapper.reader(new AvroSchema(schema))
                    .with(AvroReadFeature.READ_SINGLE_OBJECT_ENCODING)
                    .readTree(bytes);
            assertEquals("DONE", result.get("status").asString());
            assertEquals(2, result.get("line").get("qty").asInt());
        }
    }

    @Test
    public void testReadNonRecordRoots() throws Exception
    {
        final Object[][] cases = new Object[][] {
            { "'string'", "abc", "\"abc\"" },
            { "'long'", 123L, "123" },
            { "'null'", null, "null" },
            { "['null','string']", "x", "\"x\"" },
            { "{'type':'array','items':'int'}", List.of(1, 2), "[1,2]" },
            { "{'type':'map','values':'int'}", Map.of("k", 3), "{\"k\":3}" },
            { "{'type':'enum','name':'Size','symbols':['S','M']}",
                new GenericData.EnumSymbol(null, "M"), "\"M\"" },
            { "{'type':'fixed','name':'Pair','size':2}",
                new GenericData.Fixed(null, new byte[] { 1, 2 }), "\"AQI=\"" }
        };
        for (Object[] c : cases) {
            final Schema schema = new Schema.Parser().parse(a2q((String) c[0]));
            final byte[] value = toByteArray(new BinaryMessageEncoder<Object>(GenericData.get(), schema)
                    .encode(c[1]));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(value);
            out.write(value);
            final byte[] bytes = out.toByteArray();

            for (AvroMapper mapper : MAPPERS) {
                ObjectReader r = mapper.reader(new AvroSchema(schema))
                        .with(AvroReadFeature.READ_SINGLE_OBJECT_ENCODING)
                        .without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
                for (boolean stream : new boolean[] { false, true }) {
                    try (JsonParser p = stream
                            ? r.createParser(new ThrottledInputStream(bytes, 3))
                            : r.createParser(bytes)) {
                        assertEquals(c[2], r.readTree(p).toString());
                        assertEquals(c[2], r.readTree(p).toString());
                        assertNull(p.nextToken());
                    }
                }
            }
        }
    }

    @Test
    public void testHeaderNotExposedAsBinaryValue() throws Exception
    {
        byte[] bytes = encodeSingleObject(APACHE_SCHEMA, "apm");
        Schema stringSchema = Schema.create(Schema.Type.STRING);
        byte[] base64 = toByteArray(new BinaryMessageEncoder<Object>(GenericData.get(), stringSchema)
                .encode("AQI="));

        for (AvroMapper mapper : MAPPERS) {
            try (JsonParser p = singleObjectReader(mapper).createParser(bytes)) {
                assertToken(JsonToken.START_OBJECT, p.nextToken());
                assertNull(p.getEmbeddedObject());
            }
            byte[] value = mapper.readerFor(byte[].class)
                    .with(new AvroSchema(stringSchema))
                    .with(AvroReadFeature.READ_SINGLE_OBJECT_ENCODING)
                    .readValue(base64);
            assertArrayEquals(new byte[] { 1, 2 }, value);
        }
    }

    /**
     * Contrast: regular Avro binary encoding (NOT Single Object Encoding) works fine.
     */
    @Test
    public void testReadRegularBinaryEncoding() throws Exception
    {
        byte[] bytes = encodeBinary(APACHE_SCHEMA, "apm");
        // First byte should be 0x06 (zigzag VarInt for length 3), not 0xC3
        assertEquals((byte) 0x06, bytes[0]);

        for (AvroMapper mapper : MAPPERS) {
            JsonNode result = mapper.reader(JACKSON_SCHEMA).readTree(bytes);
            assertNotNull(result);
            assertEquals("apm", result.get("name").asString());
        }
    }

    private ObjectReader singleObjectReader(AvroMapper mapper) {
        return mapper.reader(JACKSON_SCHEMA)
                .with(AvroReadFeature.READ_SINGLE_OBJECT_ENCODING);
    }

    private static byte[] encodeSingleObject(Schema schema, String name) throws Exception
    {
        BinaryMessageEncoder<GenericRecord> encoder =
                new BinaryMessageEncoder<>(GenericData.get(), schema);
        return toByteArray(encoder.encode(record(schema, name)));
    }

    private static byte[] encodeBinary(Schema schema, String name) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BinaryEncoder binaryEncoder = EncoderFactory.get().binaryEncoder(out, null);
        DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(schema);
        writer.write(record(schema, name), binaryEncoder);
        binaryEncoder.flush();
        return out.toByteArray();
    }

    private static GenericRecord record(Schema schema, String name) {
        GenericRecord record = new GenericData.Record(schema);
        record.put("name", name);
        return record;
    }

    private static byte[] toByteArray(ByteBuffer buf) {
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return bytes;
    }
}
