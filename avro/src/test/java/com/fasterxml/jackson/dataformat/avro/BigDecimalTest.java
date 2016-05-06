package com.fasterxml.jackson.dataformat.avro;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class BigDecimalTest extends AvroTestBase
{
    public static class NamedAmount {
        public final String name;
        public final BigDecimal amount;

        @JsonCreator
        public NamedAmount(@JsonProperty("name") String name,
                           @JsonProperty("amount") double amount) {
            this.name = name;
            this.amount = BigDecimal.valueOf(amount);
        }
    }

    public void testSerializeBigDecimal() throws Exception {
        AvroMapper mapper = new AvroMapper();
        AvroSchema schema = mapper.schemaFor(NamedAmount.class);

        byte[] bytes = mapper.writer(schema)
                .writeValueAsBytes(new NamedAmount("peter", 42.0));

        NamedAmount result = mapper.reader(schema).forType(NamedAmount.class).readValue(bytes);

        assertEquals("peter", result.name);
        assertEquals(BigDecimal.valueOf(42.0), result.amount);
    }
}
