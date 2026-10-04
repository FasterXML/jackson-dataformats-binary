package tools.jackson.dataformat.avro.interop.records;

import java.io.IOException;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.reflect.Union;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.dataformat.avro.AvroMapper;
import tools.jackson.dataformat.avro.AvroSchema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tools.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.getJacksonSchema;
import static tools.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonDeserialize;
import static tools.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonSerialize;
import static tools.jackson.dataformat.avro.interop.InteropTestBase.type;

// [dataformats-binary#812]: record type id that does not resolve to a class
// must fall back to the base type (here `Object`, so result is a `Map`).
// (namespace changed from "bad-namespace" as Avro 1.12 rejects "-" in namespaces)
public class RecordWithMissingType812Test {

    public static class WrapperOuter<T> {

        @JsonProperty(required = true)
        public T inner;
    }

    public static class WrapperInner<T> {

        @JsonProperty(required = true)
        public T holder;

    }

    public static class Holder<T> {

        @JsonProperty(required = true)
        public T value;

    }

    @Union({ Cat.class })
    public interface Animal { }

    public static class Cat implements Animal {
        public String name;
    }

    public static class Pet {
        public Animal animal;
    }

    // Schema for WrapperOuter<WrapperInner<Holder<Double>>>, but with the namespace changed so that the POJOs can't be resolved
    public static final String SCHEMA =
        "{\n  \"type\" : \"record\",\n  \"name\" : \"WrapperOuter\",\n  \"namespace\" : \"bad_namespace\",\n"
            + "  \"fields\" : [ {\n    \"name\" : \"inner\",\n    \"type\" : {\n      \"type\" : \"record\",\n"
            + "      \"name\" : \"WrapperInner\",\n      \"fields\" : [ {\n        \"name\" : \"holder\",\n"
            + "        \"type\" : {\n          \"type\" : \"record\",\n          \"name\" : \"Holder\",\n"
            + "          \"fields\" : [ {\n            \"name\" : \"value\",\n            \"type\" : {\n"
            + "              \"type\" : \"double\",\n              \"java-class\" : \"java.lang.Double\"\n            }\n"
            + "          } ]\n        }\n      } ]\n    }\n  } ]\n}";

    // [dataformats-binary#812]
    @SuppressWarnings("unchecked")
    @Test
    public void testRecordWithPolymorphicKeyDeserialization() throws IOException {
        Schema schema = getJacksonSchema(type(WrapperOuter.class, type(WrapperInner.class, type(Holder.class, Double.class))));
        Holder<Double> holder = new Holder<>();
        holder.value = 10.5D;
        WrapperInner<Holder<Double>> inner = new WrapperInner<>();
        inner.holder = holder;
        WrapperOuter<WrapperInner<Holder<Double>>> outer = new WrapperOuter<>();
        outer.inner = inner;

        byte[] data = jacksonSerialize(schema, outer);

        Object result = jacksonDeserialize((new Schema.Parser()).parse(SCHEMA), Object.class, data);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<String, Map<String, Map<String, Double>>>) result).get("inner").get("holder").get("value")).isEqualTo(10.5D);
    }

    // [dataformats-binary#812]: fallback is only for `Object`, not for `@Union` types
    @Test
    public void testUnionWithUnknownTypeIdFails() throws Exception {
        AvroMapper mapper = new AvroMapper();
        AvroSchema schema = mapper.schemaFor(Pet.class);
        Pet pet = new Pet();
        Cat cat = new Cat();
        cat.name = "Felix";
        pet.animal = cat;
        byte[] data = mapper.writer(schema).writeValueAsBytes(pet);

        // Same schema, but namespace changed so that `Cat` can't be resolved
        String json = schema.getAvroSchema().toString();
        String ns = Pet.class.getName().substring(0, Pet.class.getName().lastIndexOf('.'));
        AvroSchema badSchema = new AvroSchema(new Schema.Parser().parse(
                json.replace("\"namespace\":\"" + ns, "\"namespace\":\"bad_namespace." + ns)));

        assertThatThrownBy(() -> mapper.readerFor(Pet.class).with(badSchema).readValue(data))
            .isInstanceOf(InvalidTypeIdException.class)
            .hasMessageContaining("bad_namespace.")
            .hasMessageContaining("no such class found")
            .hasMessageContaining("'animal'");
    }

    // [dataformats-binary#812]: resolvable type ids must still go through all
    // standard checks, like `FAIL_ON_SUBTYPE_CLASS_NOT_REGISTERED`
    @Test
    public void testFailOnSubtypeClassNotRegistered() throws Exception {
        AvroMapper mapper = AvroMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_SUBTYPE_CLASS_NOT_REGISTERED)
                .build();
        AvroSchema schema = mapper.schemaFor(Cat.class);
        Cat cat = new Cat();
        cat.name = "Felix";
        byte[] data = mapper.writer(schema).writeValueAsBytes(cat);

        assertThatThrownBy(() -> mapper.readerFor(Object.class).with(schema).readValue(data))
            .isInstanceOf(InvalidTypeIdException.class)
            .hasMessageContaining("FAIL_ON_SUBTYPE_CLASS_NOT_REGISTERED");
    }
}
