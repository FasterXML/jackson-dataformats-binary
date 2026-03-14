package tools.jackson.dataformat.avro.tofix;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.message.BinaryMessageEncoder;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.avro.AvroMapper;
import tools.jackson.dataformat.avro.AvroSchema;
import tools.jackson.dataformat.avro.testutil.failure.JacksonTestFailureExpected;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproducer for [dataformats-binary#343]: decoding data encoded with Apache Avro's
 * Single Object Encoding (magic bytes 0xC3 0x01 + 8-byte schema fingerprint + payload)
 * fails with "Invalid length indicator for String: -98".
 */
public class SingleObjectEncoding343Test
{
    private static final String SCHEMA_JSON = "{"
        + "\"type\": \"record\","
        + "\"name\": \"SampleRecord\","
        + "\"fields\": ["
        + "  {\"name\": \"name\", \"type\": \"string\"}"
        + "]}";

    private final AvroMapper MAPPER = new AvroMapper();

    /**
     * Test showing the failure: data encoded with Apache Avro's BinaryMessageEncoder
     * (Single Object Encoding) cannot be decoded by Jackson's AvroMapper.
     * The first two bytes are the magic 0xC3 0x01 which decode as zigzag VarInt -98,
     * causing "Invalid length indicator for String: -98".
     */
    @JacksonTestFailureExpected
    @Test
    public void testReadSingleObjectEncoding() throws Exception
    {
        Schema apacheSchema = new Schema.Parser().parse(SCHEMA_JSON);
        AvroSchema jacksonSchema = new AvroSchema(apacheSchema);

        // Encode using Apache Avro's Single Object Encoding (BinaryMessageEncoder),
        // which is what SpecificRecord.toByteBuffer() uses in Apache Avro 1.8+.
        // The encoded bytes start with magic: 0xC3 0x01
        BinaryMessageEncoder<GenericRecord> encoder =
                new BinaryMessageEncoder<>(GenericData.get(), apacheSchema);
        GenericRecord record = new GenericData.Record(apacheSchema);
        record.put("name", "apm");
        ByteBuffer encoded = encoder.encode(record);
        byte[] bytes = toByteArray(encoded);

        // Verify the magic bytes are present (the root cause of the failure)
        assertEquals((byte) 0xC3, bytes[0], "First magic byte should be 0xC3");
        assertEquals((byte) 0x01, bytes[1], "Second magic byte should be 0x01");

        // This currently fails with:
        // "Invalid length indicator for String: -98"
        // because Jackson tries to decode 0xC3 0x01 as a zigzag VarInt string length
        JsonNode result = MAPPER.reader(jacksonSchema).readTree(bytes);
        assertNotNull(result);
        assertEquals("apm", result.get("name").asText());
    }

    /**
     * Contrast: regular Avro binary encoding (NOT Single Object Encoding) works fine.
     */
    @Test
    public void testReadRegularBinaryEncoding() throws Exception
    {
        Schema apacheSchema = new Schema.Parser().parse(SCHEMA_JSON);
        AvroSchema jacksonSchema = new AvroSchema(apacheSchema);

        // Encode using regular binary encoder (no SOE header)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BinaryEncoder binaryEncoder = EncoderFactory.get().binaryEncoder(out, null);
        DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(apacheSchema);
        GenericRecord record = new GenericData.Record(apacheSchema);
        record.put("name", "apm");
        writer.write(record, binaryEncoder);
        binaryEncoder.flush();
        byte[] bytes = out.toByteArray();

        // First byte should be 0x06 (zigzag VarInt for length 3), not 0xC3
        assertEquals((byte) 0x06, bytes[0]);

        JsonNode result = MAPPER.reader(jacksonSchema).readTree(bytes);
        assertNotNull(result);
        assertEquals("apm", result.get("name").asText());
    }

    private static byte[] toByteArray(ByteBuffer buf) {
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return bytes;
    }
}
