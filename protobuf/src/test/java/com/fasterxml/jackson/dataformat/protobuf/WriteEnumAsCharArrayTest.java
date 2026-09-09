package com.fasterxml.jackson.dataformat.protobuf;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for writing enum-valued fields through the {@code char[]} variant of
 * {@code writeString()}, which used to fall through and additionally emit the
 * value as a length-prefixed String.
 */
public class WriteEnumAsCharArrayTest extends ProtobufTestBase
{
    private final ProtobufMapper MAPPER = newObjectMapper();

    @Test
    public void testEnumViaCharArrayMatchesString() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_SEARCH_REQUEST);

        byte[] viaString = _write(schema, false);
        byte[] viaCharArray = _write(schema, true);

        assertEquals(2, viaString.length);
        assertArrayEquals(viaString, viaCharArray);
    }

    @Test
    public void testEnumViaCharArrayRoundTrips() throws Exception
    {
        ProtobufSchema schema = ProtobufSchemaLoader.std.parse(PROTOC_SEARCH_REQUEST);
        SearchRequest result = MAPPER.readerFor(SearchRequest.class).with(schema)
                .readValue(_write(schema, true));
        assertEquals(Corpus.WEB, result.corpus);
    }

    private byte[] _write(ProtobufSchema schema, boolean useCharArray) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator g = MAPPER.writer(schema).createGenerator(bytes)) {
            g.writeStartObject();
            g.writeFieldName("corpus");
            if (useCharArray) {
                char[] ch = "WEB".toCharArray();
                g.writeString(ch, 0, ch.length);
            } else {
                g.writeString("WEB");
            }
            g.writeEndObject();
        }
        return bytes.toByteArray();
    }
}
