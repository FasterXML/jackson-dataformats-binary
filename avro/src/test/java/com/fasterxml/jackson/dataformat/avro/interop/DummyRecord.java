package com.fasterxml.jackson.dataformat.avro.interop;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class DummyRecord {
    @JsonProperty(required = true)
    private String firstValue;
    @JsonProperty(required = true)
    private int secondValue;

    protected DummyRecord() { }
    public DummyRecord(String fv, int sv) {
        firstValue = fv;
        secondValue = sv;
    }
}
