package tools.jackson.dataformat.avro.schema;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.type.TypeFactory;

import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class DateTimeVisitor_builtAvroSchemaTest {

    private static final TypeFactory TYPE_FACTORY = TypeFactory.defaultInstance();

    @Parameter(0)
    public Class<?> testClass;

    @Parameter(1)
    public JsonParser.NumberType givenNumberType;

    @Parameter(2)
    public Schema.Type expectedAvroType;

    @Parameter(3)
    public String expectedLogicalType;

    @Parameters(name = "With {0} and number type {1}")
    public static Collection<?> testData() {
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
                        "time-millis"},
        });
    }

    @Test
    public void builtAvroSchemaTest() {
        // GIVEN
        DateTimeVisitor dateTimeVisitor = new DateTimeVisitor(TYPE_FACTORY.constructSimpleType(testClass, null));
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
