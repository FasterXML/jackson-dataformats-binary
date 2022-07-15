package tools.jackson.dataformat.avro.interop.records;

import java.io.IOException;
import java.util.Objects;

import org.junit.Test;

import tools.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests serializing wrapper types for primitives on records
 */
public class RecordWithPrimitiveWrapperTest extends InteropTestBase
{
    public static class TestRecord {
        public Byte      byteField      = 0;
        public Short     shortField     = 0;
        public Character characterField = 'A';
        public Integer   integerField   = 0;
        public Long      longField      = 0L;
        public Float     floatField     = 0F;
        public Double    doubleField    = 0D;
        public String    stringField    = "";

        @Override
        public int hashCode() {
            return byteField + shortField + characterField + integerField
                    + Objects.hash(stringField);
        }

        @Override
        public String toString() {
            return "TestRecord";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof TestRecord)) return false;
            TestRecord other = (TestRecord) o;
            return Objects.equals(byteField, other.byteField)
                    && Objects.equals(shortField, other.shortField)
                    && Objects.equals(characterField, other.characterField)
                    && Objects.equals(integerField, other.integerField)
                    && Objects.equals(longField, other.longField)
                    && Objects.equals(floatField, other.floatField)
                    && Objects.equals(doubleField, other.doubleField)
                    && Objects.equals(stringField, other.stringField)
                ;
        }
    }

    @Test
    public void testByteField() throws IOException {
        TestRecord record = new TestRecord();
        record.byteField = Byte.MAX_VALUE;
        TestRecord result = roundTrip(record);
        assertThat(result.byteField).isEqualTo(record.byteField);
    }

    @Test
    public void testCharacterField() throws IOException {
        TestRecord record = new TestRecord();
        record.characterField = Character.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.characterField).isEqualTo(record.characterField);
    }

    @Test
    public void testDoubleField() throws IOException {
        TestRecord record = new TestRecord();
        record.doubleField = Double.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.doubleField).isEqualTo(record.doubleField);
    }

    @Test
    public void testFloatField() throws IOException {
        TestRecord record = new TestRecord();
        record.floatField = Float.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.floatField).isEqualTo(record.floatField);
    }

    @Test
    public void testInteger() throws IOException {
        TestRecord record = new TestRecord();
        record.integerField = Integer.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.integerField).isEqualTo(record.integerField);
    }

    @Test
    public void testLongField() throws IOException {
        TestRecord record = new TestRecord();
        record.longField = Long.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.longField).isEqualTo(record.longField);
    }

    @Test
    public void testShortField() throws IOException {
        TestRecord record = new TestRecord();
        record.shortField = Short.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.shortField).isEqualTo(record.shortField);
    }

    @Test
    public void testStringField() throws IOException {
        TestRecord record = new TestRecord();
        record.stringField = "Hello World";
        //
        TestRecord result = roundTrip(record);
        //
        assertThat(result.stringField).isEqualTo(record.stringField);
    }
}
