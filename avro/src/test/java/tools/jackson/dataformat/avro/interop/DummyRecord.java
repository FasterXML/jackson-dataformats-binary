package tools.jackson.dataformat.avro.interop;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DummyRecord {
    @JsonProperty(required = true)
    public String firstValue;
    @JsonProperty(required = true)
    public int secondValue;

    protected DummyRecord() { }
    public DummyRecord(String fv, int sv) {
        firstValue = fv;
        secondValue = sv;
    }

    @Override
    public String toString() {
        return String.format("[first=%s,second=%s]", firstValue, secondValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstValue, secondValue);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof DummyRecord)) return false;
        return _equals((DummyRecord) o);
    }

    protected boolean _equals(DummyRecord other) {
        return Objects.equals(firstValue, other.firstValue)
                && Objects.equals(secondValue, other.secondValue);
    }
}
