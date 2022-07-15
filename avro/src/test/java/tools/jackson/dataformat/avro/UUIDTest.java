package tools.jackson.dataformat.avro;

import java.util.UUID;

// @since 2.10
public class UUIDTest extends AvroTestBase
{
    static class UUIDWrapper {
        public UUID id;

        protected UUIDWrapper() { }
        public UUIDWrapper(UUID u) { id = u; }
    }

    private final AvroMapper MAPPER = newMapper();

    // 10-Sep-2019, tatu: as per [dataformats-binary#179], should really serialize
    //   UUID as binary, but due to various complications can not make it work
    //   safely and reliably with 2.10; can only add some foundational support.
    public void testUUIDRoundtrip() throws Exception
    {
        final AvroSchema schema = MAPPER.schemaFor(UUIDWrapper.class);
        UUIDWrapper input = new UUIDWrapper(UUID.nameUUIDFromBytes("BOGUS".getBytes("UTF-8")));
        byte[] avro = MAPPER.writer(schema).writeValueAsBytes(input);

        UUIDWrapper output = MAPPER.readerFor(UUIDWrapper.class)
                .with(schema)
                .readValue(avro);

        assertEquals(input.id, output.id);
    }
}
