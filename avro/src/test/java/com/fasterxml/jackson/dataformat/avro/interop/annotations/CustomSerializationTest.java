package com.fasterxml.jackson.dataformat.avro.interop.annotations;

import java.io.IOException;
import java.util.Objects;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.junit.Test;

import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonDeserialize;
import static com.fasterxml.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonSerialize;
import static org.assertj.core.api.Assertions.assertThat;

public final class CustomSerializationTest {
    @JsonSerialize(using = House.Serializer.class)
    @JsonDeserialize(using = House.Deserializer.class)
    public final static class House {
        public String ownerName;

        public House(final String ownerName) {
            this.ownerName = ownerName;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof House && ownerName.equals(((House) other).ownerName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ownerName);
        }

        public static class Serializer extends JsonSerializer<House> {
            @Override
            public void serialize(final House house,
                final JsonGenerator generator,
                final SerializerProvider serializers)
            {
                generator.writeStartObject();
                generator.writeFieldName("owner");
                generator.writeStartObject();
                generator.writeFieldName("name");
                generator.writeObject(house.ownerName);
                generator.writeEndObject();
                generator.writeEndObject();
            }
        }

        public static class Deserializer extends JsonDeserializer<House> {
            @Override
            public House deserialize(final JsonParser parser,
                final DeserializationContext context)
            {
                // startObject
                parser.nextToken();
                // fieldName("owner")
                parser.nextToken();
                // writeStartObject
                parser.nextToken();
                // fieldName("name")
                parser.nextToken();

                String ownerName = parser.readValueAs(String.class);

                // endObject
                parser.nextToken();
                // endObject
                parser.nextToken();

                return new House(ownerName);
            }
        }
    }

    @Test
    public void testUnionCustomDeSerialization() throws IOException {
        Schema schema = SchemaBuilder.builder(House.class.getPackage().getName())
            .record(House.class.getSimpleName())
            .fields()
            .name("owner")
            .type()
            .optional()
            .record("Owner")
                .fields()
                .name("name")
                .type().nullable().stringType()
                .noDefault()
            .endRecord()
            .endRecord();

        House house = new House("Jackson");

        byte[] bytes = jacksonSerialize(schema, house);
        House result = jacksonDeserialize(schema, House.class, bytes);

        assertThat(result).isEqualTo(house);
    }
}
