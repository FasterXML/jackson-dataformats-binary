package tools.jackson.dataformat.avro.tofix;

import java.io.IOException;
import java.util.Map;

import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.dataformat.avro.testutil.failure.JacksonTestFailureExpected;

import static org.assertj.core.api.Assertions.assertThat;
import static tools.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.getJacksonSchema;
import static tools.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonDeserialize;
import static tools.jackson.dataformat.avro.interop.ApacheAvroInteropUtil.jacksonSerialize;
import static tools.jackson.dataformat.avro.interop.InteropTestBase.type;

// [dataformats-binary#812]: passes on 2.x, where an unresolvable type id falls
// back to the base type (here `Object`, so result is a `Map`): on 3.x that fallback
// was removed from `AvroTypeIdResolver` and `AvroTypeDeserializer._handleUnknownTypeId()`,
// so `InvalidTypeIdException` is thrown instead.
// (test never ran before as class name lacked `Test` suffix; namespace also changed
// from "bad-namespace" as Avro 1.12 rejects "-" in namespaces)
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
    @JacksonTestFailureExpected
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
}
