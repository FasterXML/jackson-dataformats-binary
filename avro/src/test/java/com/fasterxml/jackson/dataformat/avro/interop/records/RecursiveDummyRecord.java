package com.fasterxml.jackson.dataformat.avro.interop.records;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.avro.reflect.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.avro.interop.DummyRecord;
import com.fasterxml.jackson.dataformat.avro.interop.InteropTestBase.DummyEnum;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class RecursiveDummyRecord extends DummyRecord {
    @Nullable
    private DummyRecord next;

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
}
