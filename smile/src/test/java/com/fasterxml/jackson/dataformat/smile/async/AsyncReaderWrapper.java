package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.NumberType;
import com.fasterxml.jackson.core.JsonToken;

public abstract class AsyncReaderWrapper
{
    protected final JsonParser _streamReader;

    protected AsyncReaderWrapper(JsonParser sr) {
        _streamReader = sr;
    }

    public JsonToken currentToken() throws IOException {
        return _streamReader.currentToken();
    }
    public String currentText() throws IOException {
        return _streamReader.getText();
    }

    public abstract JsonToken nextToken() throws IOException;

    public int getIntValue() throws IOException { return _streamReader.getIntValue(); }
    public long getLongValue() throws IOException { return _streamReader.getLongValue(); }
    public float getFloatValue() throws IOException { return _streamReader.getFloatValue(); }
    public double getDoubleValue() throws IOException { return _streamReader.getDoubleValue(); }
    public BigInteger getBigIntegerValue() throws IOException { return _streamReader.getBigIntegerValue(); }
    public BigDecimal getBigDecimalValue() throws IOException { return _streamReader.getDecimalValue(); }

    public NumberType getNumberType() throws IOException { return _streamReader.getNumberType(); }

    public boolean isClosed() {
        return _streamReader.isClosed();
    }
}
