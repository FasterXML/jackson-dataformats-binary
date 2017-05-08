package com.fasterxml.jackson.dataformat.smile.async;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
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

    public boolean isClosed() {
        return _streamReader.isClosed();
    }
}
