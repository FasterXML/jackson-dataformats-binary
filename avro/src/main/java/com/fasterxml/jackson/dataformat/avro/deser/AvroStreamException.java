package com.fasterxml.jackson.dataformat.avro.deser;

import com.fasterxml.jackson.core.JsonProcessingException;

public class AvroStreamException extends JsonProcessingException {
    public AvroStreamException(String message, Throwable t) {
        super(message, t);
    }
}
