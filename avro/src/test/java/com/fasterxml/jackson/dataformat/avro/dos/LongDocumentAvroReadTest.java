package com.fasterxml.jackson.dataformat.avro.dos;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;

import com.fasterxml.jackson.dataformat.avro.*;
import com.fasterxml.jackson.dataformat.avro.apacheimpl.ApacheAvroFactory;

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

    private final static int MAX_DOC_LEN = 50_000;

    private final AvroMapper MAPPER_VANILLA = newMapper();

    private final AvroSchema ITEMS_SCHEMA;
    {
        try {
            ITEMS_SCHEMA = MAPPER_VANILLA.schemaFor(Items.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void testLongDocumentConstraint() throws Exception
    {
        // Need a bit longer than minimum since checking is approximate, not exact
        byte[] doc = createBigDoc(60_000);
        for (boolean apache : new boolean[] { false, true }) {
            AvroMapper mapper = constrainedMapper(apache);
            _testLongDocumentConstraint(mapper, doc, true);
            _testLongDocumentConstraint(mapper, doc, false);
        }
    }

    public void testLongDocumentNoConstraint() throws Exception
    {
        byte[] doc = createBigDoc(60_000);
        for (AvroMapper mapper : new AvroMapper[] {
                MAPPER_VANILLA, AvroMapper.builder(new ApacheAvroFactory()).build() }) {
            try (JsonParser p = mapper.reader().with(ITEMS_SCHEMA)
                    .createParser(new ByteArrayInputStream(doc))) {
                while (p.nextToken() != null) { }
            }
            try (JsonParser p = mapper.reader().with(ITEMS_SCHEMA).createParser(doc)) {
                while (p.nextToken() != null) { }
            }
        }
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
            final String msg = e.getMessage();
            assertTrue("unexpected message: "+msg, msg.contains("Document length ("));
            assertTrue("unexpected message: "+msg,
                    msg.contains("exceeds the maximum allowed ("+MAX_DOC_LEN));
        }
    }

    private AvroMapper constrainedMapper(boolean apacheDecoder) {
        // 2.18 `AvroFactoryBuilder` does not (yet) honor Apache decoder setting, so
        // have to construct `ApacheAvroFactory` directly
        AvroFactory f = apacheDecoder ? new ApacheAvroFactory() : new AvroFactory();
        f.setStreamReadConstraints(StreamReadConstraints.builder()
                .maxDocumentLength(MAX_DOC_LEN).build());
        return AvroMapper.builder(f).build();
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
        assertTrue("doc.length="+doc.length, doc.length > size);
        return doc;
    }
}
