package tools.jackson.dataformat.avro.constraints;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.avro.io.DecoderFactory;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.databind.DeserializationFeature;

import tools.jackson.dataformat.avro.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#785]: `StreamReadConstraints.maxDocumentLength` for Avro
public class LongDocumentAvroReadTest extends AvroTestBase
{
    public static class Item {
        public String id;
        public int size;
        public long stuff;
    }

    public static class Items {
        public List<Item> items = new ArrayList<>();
    }

    // NOTE: Avro requires named types to match, hence same record name for both
    final static String BLOB_SCHEMA_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'Blob',\n"+
            " 'fields':[\n"+
            "    { 'name':'name', 'type':'string' },\n"+
            "    { 'name':'data', 'type':'bytes' }\n"+
            " ]\n"+
            "}\n");

    // Reader-side schema without `data`: forces writer-side `bytes` value to be skipped
    final static String BLOB_NO_DATA_SCHEMA_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'Blob',\n"+
            " 'fields':[\n"+
            "    { 'name':'name', 'type':'string' }\n"+
            " ]\n"+
            "}\n");

    final static String TINY_SCHEMA_JSON = aposToQuotes("{\n"+
            " 'type':'record',\n"+
            " 'name':'Tiny',\n"+
            " 'fields':[\n"+
            "    { 'name':'x', 'type':'int' }\n"+
            " ]\n"+
            "}\n");

    private final static int MAX_DOC_LEN = 50_000;

    // Decoder variants to verify: native, Apache (buffering) and Apache (direct)
    private final static int MODE_NATIVE = 0;
    private final static int MODE_APACHE = 1;
    private final static int MODE_APACHE_DIRECT = 2;

    // NOTE: `MODE_APACHE_DIRECT` not included: Apache `DirectBinaryDecoder.isEnd()`
    // throws `UnsupportedOperationException`, so non-buffering Apache decoding does
    // not currently work at all (pre-existing issue, unrelated to #785)
    private final static int[] ALL_MODES = new int[] {
            MODE_NATIVE, MODE_APACHE
    };

    private final AvroMapper MAPPER_VANILLA = newMapper();

    private final AvroSchema ITEMS_SCHEMA;
    {
        try {
            ITEMS_SCHEMA = MAPPER_VANILLA.schemaFor(Items.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testLongDocumentConstraint() throws Exception
    {
        // Need a bit longer than minimum since checking is approximate, not exact
        byte[] doc = createBigDoc(60_000);
        for (int mode : ALL_MODES) {
            AvroMapper mapper = constrainedMapper(mode);
            _testLongDocumentConstraint(mapper, doc, true);
            _testLongDocumentConstraint(mapper, doc, false);
        }
    }

    @Test
    public void testLongDocumentNoConstraint() throws Exception
    {
        byte[] doc = createBigDoc(60_000);
        for (AvroMapper mapper : new AvroMapper[] {
                MAPPER_VANILLA, new AvroMapper(AvroFactory.builderWithApacheDecoder().build()) }) {
            try (JsonParser p = mapper.reader().with(ITEMS_SCHEMA)
                    .createParser(new ByteArrayInputStream(doc))) {
                while (p.nextToken() != null) { }
            }
            try (JsonParser p = mapper.reader().with(ITEMS_SCHEMA).createParser(doc)) {
                while (p.nextToken() != null) { }
            }
        }
    }

    // [dataformats-binary#785]: big `bytes` value is read straight from `InputStream`,
    // bypassing input buffer, and must still count towards document length
    @Test
    public void testLongBinaryValueConstraint() throws Exception
    {
        byte[] doc = createBinaryDoc(200_000);
        for (int mode : ALL_MODES) {
            AvroMapper mapper = constrainedMapper(mode);
            AvroSchema schema = mapper.schemaFrom(BLOB_SCHEMA_JSON);
            try (JsonParser p = mapper.reader().with(schema)
                    .createParser(new ByteArrayInputStream(doc))) {
                while (p.nextToken() != null) { }
                fail("expected StreamConstraintsException (mode="+mode+")");
            } catch (StreamConstraintsException e) {
                _verifyConstraintException(e);
            }
        }
    }

    // Same as above but for the case where the big `bytes` value is skipped
    // (writer schema has field reader schema does not)
    @Test
    public void testSkippedLongBinaryValueConstraint() throws Exception
    {
        byte[] doc = createBinaryDoc(200_000);
        AvroMapper mapper = constrainedMapper(MODE_NATIVE);
        AvroSchema schema = mapper.schemaFrom(BLOB_SCHEMA_JSON)
                .withReaderSchema(mapper.schemaFrom(BLOB_NO_DATA_SCHEMA_JSON));
        try (JsonParser p = mapper.reader().with(schema)
                .createParser(new ByteArrayInputStream(doc))) {
            while (p.nextToken() != null) { }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            _verifyConstraintException(e);
        }
    }

    // [dataformats-binary#785]: Apache decoder reads ahead of the decoding position,
    // by up to its buffer size: must not fail a single short value read from a
    // stream that holds more content past it
    @Test
    public void testShortValueFromLongStream() throws Exception
    {
        final int SHORT_MAX_DOC_LEN = 1000;
        final AvroSchema schema = MAPPER_VANILLA.schemaFrom(TINY_SCHEMA_JSON);

        // Stream with lots of content, but we only read the first (1-byte) value
        byte[] one = MAPPER_VANILLA.writer(schema)
                .writeValueAsBytes(Collections.singletonMap("x", 42));
        byte[] many = new byte[one.length * 50_000];
        for (int i = 0; i < many.length; i += one.length) {
            System.arraycopy(one, 0, many, i, one.length);
        }
        assertTrue(many.length > 8 * SHORT_MAX_DOC_LEN, "many.length="+many.length);

        for (int mode : ALL_MODES) {
            AvroMapper mapper = constrainedMapper(mode, SHORT_MAX_DOC_LEN);
            // NOTE: 3.x enables `FAIL_ON_TRAILING_TOKENS` by default; here we do
            //   want to read just the first value out of a longer stream
            Map<?,?> value = mapper.readerFor(Map.class).with(schema)
                    .without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(new ByteArrayInputStream(many));
            assertEquals(42, value.get("x"), "mode="+mode);
        }
    }

    // [dataformats-binary#806]: limit below the decoder's buffer size must be enforced
    // too, and the same way whichever decoder and source type are used
    @Test
    public void testShortLimitEnforcedForAllModes() throws Exception
    {
        final int SHORT_LIMIT = 100;
        byte[] doc = createBigDoc(5_000);
        assertTrue(doc.length > SHORT_LIMIT, "doc.length="+doc.length);
        assertTrue(doc.length < DecoderFactory.get().getConfiguredBufferSize(),
                "doc.length="+doc.length);

        for (int mode : ALL_MODES) {
            AvroMapper mapper = constrainedMapper(mode, SHORT_LIMIT);
            for (boolean stream : new boolean[] { true, false }) {
                try (JsonParser p = stream
                        ? mapper.reader().with(ITEMS_SCHEMA).createParser(new ByteArrayInputStream(doc))
                        : mapper.reader().with(ITEMS_SCHEMA).createParser(doc)) {
                    while (p.nextToken() != null) { }
                    fail("expected StreamConstraintsException (mode="+mode+", stream="+stream+")");
                } catch (StreamConstraintsException e) {
                    assertTrue(e.getMessage().contains("exceeds the maximum allowed ("+SHORT_LIMIT),
                            "unexpected message: "+e.getMessage());
                }
            }
        }
    }

    // [dataformats-binary#806]: reported length must be a real count -- at least the limit
    // that was breached, never more than the document actually holds
    @Test
    public void testReportedLengthIsPlausible() throws Exception
    {
        final int LIMIT = 1_000;
        byte[] doc = createBigDoc(60_000);

        for (int mode : ALL_MODES) {
            AvroMapper mapper = constrainedMapper(mode, LIMIT);
            try (JsonParser p = mapper.reader().with(ITEMS_SCHEMA)
                    .createParser(new ByteArrayInputStream(doc))) {
                while (p.nextToken() != null) { }
                fail("expected StreamConstraintsException (mode="+mode+")");
            } catch (StreamConstraintsException e) {
                long reported = _reportedLength(e.getMessage());
                assertTrue(reported > LIMIT,
                        "mode="+mode+", reported="+reported+" should exceed limit "+LIMIT);
                assertTrue(reported <= doc.length,
                        "mode="+mode+", reported="+reported+" should not exceed doc size "+doc.length);
            }
        }
    }

    private long _reportedLength(String msg) {
        int start = msg.indexOf('(');
        int end = msg.indexOf(')', start);
        assertTrue(start > 0 && end > start, "unexpected message: "+msg);
        return Long.parseLong(msg.substring(start+1, end));
    }

    private void _testLongDocumentConstraint(AvroMapper mapper, byte[] doc, boolean stream)
        throws Exception
    {
        // note: fixed-buffer case fails already on `createParser()`
        try (JsonParser p = stream
                ? mapper.reader().with(ITEMS_SCHEMA).createParser(new ByteArrayInputStream(doc))
                : mapper.reader().with(ITEMS_SCHEMA).createParser(doc)) {
            while (p.nextToken() != null) { }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            _verifyConstraintException(e);
        }
    }

    private void _verifyConstraintException(StreamConstraintsException e) {
        final String msg = e.getMessage();
        assertTrue(msg.contains("Document length ("), "unexpected message: "+msg);
        assertTrue(msg.contains("exceeds the maximum allowed ("+MAX_DOC_LEN),
                "unexpected message: "+msg);
    }

    private AvroMapper constrainedMapper(int mode) {
        return constrainedMapper(mode, MAX_DOC_LEN);
    }

    private AvroMapper constrainedMapper(int mode, int maxDocLen) {
        AvroFactoryBuilder b = (mode == MODE_NATIVE)
                ? AvroFactory.builderWithNativeDecoder()
                : AvroFactory.builderWithApacheDecoder();
        if (mode == MODE_APACHE_DIRECT) {
            b = b.disable(AvroReadFeature.AVRO_BUFFERING);
        }
        return new AvroMapper(b.streamReadConstraints(StreamReadConstraints.builder()
                .maxDocumentLength(maxDocLen).build())
                .build());
    }

    private byte[] createBinaryDoc(final int payloadSize) throws Exception
    {
        Map<String, Object> blob = new LinkedHashMap<>();
        blob.put("name", "blob");
        blob.put("data", new byte[payloadSize]);
        byte[] doc = MAPPER_VANILLA.writer(MAPPER_VANILLA.schemaFrom(BLOB_SCHEMA_JSON))
                .writeValueAsBytes(blob);
        assertTrue(doc.length > payloadSize, "doc.length="+doc.length);
        return doc;
    }

    private byte[] createBigDoc(final int size) throws Exception
    {
        Items items = new Items();
        // Each Item is ~50 bytes encoded; over-estimate count to be safe
        for (int i = 0, len = size / 40; i < len; ++i) {
            Item item = new Item();
            item.id = UUID.randomUUID().toString();
            item.size = i;
            item.stuff = Long.MAX_VALUE;
            items.items.add(item);
        }
        byte[] doc = MAPPER_VANILLA.writer(ITEMS_SCHEMA).writeValueAsBytes(items);
        assertTrue(doc.length > size, "doc.length="+doc.length);
        return doc;
    }
}
