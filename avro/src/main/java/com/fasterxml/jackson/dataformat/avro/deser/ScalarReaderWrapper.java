package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.io.BinaryDecoder;

import com.fasterxml.jackson.core.JsonToken;

final class ScalarReaderWrapper extends AvroStructureReader
{
    private final AvroScalarReader _wrappedReader;
    private final BinaryDecoder _decoder;
    private final AvroParserImpl _parser;

    public ScalarReaderWrapper(AvroScalarReader wrappedReader) {
        this(wrappedReader, null, null);
    }

    private ScalarReaderWrapper(AvroScalarReader wrappedReader,
            AvroParserImpl parser, BinaryDecoder decoder) {
        super(null, TYPE_ROOT);
        _wrappedReader = wrappedReader;
        _parser = parser;
        _decoder = decoder;
    }

    @Override
    public ScalarReaderWrapper newReader(AvroReadContext parent,
            AvroParserImpl parser, BinaryDecoder decoder) {
        return new ScalarReaderWrapper(_wrappedReader, parser, decoder);
    }

    @Override
    public JsonToken nextToken() throws IOException
    {
        if (_currToken == null) {
            JsonToken t = _wrappedReader.readValue(_parser, _decoder);
            _currToken = t;
            return t;
        }
        return null;
    }

    @Override
    protected void appendDesc(StringBuilder sb) {
        sb.append('?');
    }

    @Override
    public String nextFieldName() throws IOException {
        nextToken();
        return null;
    }
}