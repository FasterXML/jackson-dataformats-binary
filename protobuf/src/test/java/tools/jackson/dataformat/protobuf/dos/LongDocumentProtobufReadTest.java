package tools.jackson.dataformat.protobuf.dos;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.protobuf.*;
import tools.jackson.dataformat.protobuf.schema.ProtobufSchema;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// [dataformats-binary#783]: `StreamReadConstraints.maxDocumentLength` for Protobuf
public class LongDocumentProtobufReadTest extends ProtobufTestBase
{
    public static class Item {
        public String id;
        public int size;
        public long stuff;
    }

    public static class Items {
        public List<Item> items = new ArrayList<>();
    }

    private final static int MAX_DOC_LEN = 50_000;

    private final ProtobufMapper MAPPER_VANILLA = newObjectMapper();

    private final ProtobufMapper MAPPER_CONSTRAINED = new ProtobufMapper(
            ProtobufFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                    .maxDocumentLength(MAX_DOC_LEN)
                    .build())
                .build());

    private final ProtobufSchema ITEMS_SCHEMA;
    {
        try {
            ITEMS_SCHEMA = MAPPER_VANILLA.generateSchemaFor(Items.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testLongDocumentConstraint() throws Exception
    {
        // Need a bit longer than minimum since checking is approximate, not exact
        byte[] doc = createBigDoc(60_000);
        _testLongDocumentConstraint(doc, true);
        _testLongDocumentConstraint(doc, false);
    }

    @Test
    public void testLongDocumentNoConstraint() throws Exception
    {
        byte[] doc = createBigDoc(60_000);
        try (JsonParser p = protobufParser(MAPPER_VANILLA, new ByteArrayInputStream(doc))) {
            while (p.nextToken() != null) { }
        }
        try (JsonParser p = protobufParser(MAPPER_VANILLA, doc)) {
            while (p.nextToken() != null) { }
        }
    }

    private void _testLongDocumentConstraint(byte[] doc, boolean stream) throws Exception
    {
        try (JsonParser p = stream
                ? protobufParser(MAPPER_CONSTRAINED, new ByteArrayInputStream(doc))
                : protobufParser(MAPPER_CONSTRAINED, doc)) {
            while (p.nextToken() != null) { }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            final String msg = e.getMessage();
            assertTrue(msg.contains("Document length ("), "unexpected message: "+msg);
            assertTrue(msg.contains("exceeds the maximum allowed ("+MAX_DOC_LEN), "unexpected message: "+msg);
        }
    }

    private byte[] createBigDoc(final int size) throws Exception
    {
        Items items = new Items();
        // Each Item is ~50 bytes encoded
        for (int i = 0, len = size / 50 + 1; i < len; ++i) {
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

    private JsonParser protobufParser(ObjectMapper mapper, byte[] doc) throws Exception {
        return mapper.readerFor(Items.class).with(ITEMS_SCHEMA).createParser(doc);
    }

    private JsonParser protobufParser(ObjectMapper mapper, ByteArrayInputStream doc) throws Exception {
        return mapper.readerFor(Items.class).with(ITEMS_SCHEMA).createParser(doc);
    }
}
