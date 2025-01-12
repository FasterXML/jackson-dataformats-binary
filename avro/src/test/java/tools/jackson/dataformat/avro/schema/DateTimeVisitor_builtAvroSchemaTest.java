package tools.jackson.dataformat.avro.schema;

import java.time.*;
import java.util.Arrays;
import java.util.Collection;

import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.type.TypeFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class DateTimeVisitor_builtAvroSchemaTest {

    public static Collection<Object[]> testData() {
        return Arrays.asList(new Object[][]{
                // Java type  | given number type, | expected Avro type | expected logicalType
                {
                        Instant.class,
                        JsonParser.NumberType.LONG,
                        Schema.Type.LONG,
                        "timestamp-millis"},
                {
                        OffsetDateTime.class,
                        JsonParser.NumberType.LONG,
                        Schema.Type.LONG,
                        "timestamp-millis"},
                {
                        ZonedDateTime.class,
                        JsonParser.NumberType.LONG,
                        Schema.Type.LONG,
                        "timestamp-millis"},
                {
                        LocalDateTime.class,
                        JsonParser.NumberType.LONG,
                        Schema.Type.LONG,
                        "local-timestamp-millis"},
                {
                        LocalDate.class,
                        JsonParser.NumberType.INT,
                        Schema.Type.INT,
                        "date"},
                {
                        LocalTime.class,
                        JsonParser.NumberType.INT,
                        Schema.Type.INT,
                        "time-millis"
                }
        });
    }

    @ParameterizedTest(name = "With {0} and number type {1}")
    @MethodSource("testData")
    public void builtAvroSchemaTest(Class<?> testClass,
        JsonParser.NumberType givenNumberType, Schema.Type expectedAvroType,
        String expectedLogicalType)
    {
        // GIVEN
        final TypeFactory tf = TypeFactory.createDefaultInstance();
        DateTimeVisitor dateTimeVisitor = new DateTimeVisitor(tf.constructSimpleType(testClass, null));
        dateTimeVisitor.numberType(givenNumberType);

        // WHEN
        Schema actualSchema = dateTimeVisitor.builtAvroSchema();

//        System.out.println(testClass.getName() + " schema:\n" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getType()).isEqualTo(expectedAvroType);
        assertThat(actualSchema.getProp(LogicalType.LOGICAL_TYPE_PROP)).isEqualTo(expectedLogicalType);
        /**
         * Having logicalType and java-class is not valid according to
         * {@link LogicalType#validate(Schema)}
         */
        assertThat(actualSchema.getProp(SpecificData.CLASS_PROP)).isNull();
    }

}
