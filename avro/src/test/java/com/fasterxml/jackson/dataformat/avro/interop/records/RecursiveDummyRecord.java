package com.fasterxml.jackson.dataformat.avro.interop.records;

import java.util.*;

import org.apache.avro.reflect.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.interop.DummyRecord;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase.DummyEnum;

public class RecursiveDummyRecord extends DummyRecord {
    @Nullable
    @JsonProperty
    private DummyRecord next;

    @JsonProperty
    Map<String, Integer> simpleMap = new HashMap<>();

    public Map<String, RecursiveDummyRecord> recursiveMap = new HashMap<>();

    public List<Integer> requiredList = new ArrayList<>();

    @JsonProperty(required = true)
    public DummyEnum requiredEnum = DummyEnum.EAST;

    @Nullable
    public DummyEnum optionalEnum = null;

    protected RecursiveDummyRecord() { }
    public RecursiveDummyRecord(String firstValue, Integer secondValue, DummyRecord next) {
        super(firstValue, secondValue);
        this.next = next;
    }

    // hashCode, toString from parent
    
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof RecursiveDummyRecord)) return false;
        RecursiveDummyRecord other = (RecursiveDummyRecord) o;
        return _equals(other)
                && Objects.equals(next, other.next)
                && Objects.equals(simpleMap, other.simpleMap)
                && Objects.equals(recursiveMap, other.recursiveMap)
                && Objects.equals(requiredList, other.requiredList)
                && Objects.equals(requiredEnum, other.requiredEnum)
                && Objects.equals(optionalEnum, other.optionalEnum)
                ;
    }

}
