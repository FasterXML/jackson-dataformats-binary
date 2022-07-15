package tools.jackson.dataformat.avro.interop.records;

import java.io.IOException;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import tools.jackson.dataformat.avro.interop.InteropTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests serializing primitive fields on records
 */
public class RecordWithPrimitiveTest extends InteropTestBase
{
    @JsonPropertyOrder({ "byteField", "shortField", "characterField", "integerField", "longField",
        "floatField", "doubleField" })
    public static class TestRecord {
        public byte   byteField;
        public short  shortField;
        public char   characterField;
        public int    integerField;
        public long   longField;
        public float  floatField;
        public double doubleField;

        @Override
        public int hashCode() {
            return byteField + shortField + characterField + integerField
                    + (int) longField;
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
            return (byteField == other.byteField)
                    && (shortField == other.shortField)
                    && (characterField == other.characterField)
                    && (integerField == other.integerField)
                    && (longField == other.longField)
                    && (floatField == other.floatField)
                    && (doubleField == other.doubleField)
                ;
        }
    }

    @Test
    public void testByteField() throws IOException {
        TestRecord record = new TestRecord();
        record.byteField = Byte.MAX_VALUE;
        //
        TestRecord result = roundTrip(record);
        //
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
}
