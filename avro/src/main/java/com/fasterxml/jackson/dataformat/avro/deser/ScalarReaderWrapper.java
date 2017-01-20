package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.io.BinaryDecoder;

import com.fasterxml.jackson.core.JsonToken;

final class ScalarReaderWrapper extends AvroStructureReader
{
    private final AvroScalarReader _wrappedReader;
    private final BinaryDecoder _decoder;
    private final AvroParserImpl _parser;
    private final boolean _rootReader;

    public ScalarReaderWrapper(AvroScalarReader wrappedReader) {
        this(wrappedReader, null, null, false);
    }

    private ScalarReaderWrapper(AvroScalarReader wrappedReader,
            AvroParserImpl parser, BinaryDecoder decoder, boolean root) {
        super(null, TYPE_ROOT);
        _wrappedReader = wrappedReader;
        _parser = parser;
        _decoder = decoder;
        _rootReader = root;
    }

    @Override
    public ScalarReaderWrapper newReader(AvroReadContext parent,
            AvroParserImpl parser, BinaryDecoder decoder) {
        return new ScalarReaderWrapper(_wrappedReader, parser, decoder, parent.inRoot());
    }

    @Override
    public JsonToken nextToken() throws IOException
    {
        // 19-Jan-2017, tatu: May need to be called multiple times, for root-level
        //    sequences. Because of this need to check for EOF. But only after reading
        //    one token successfully...
        if (_rootReader && _decoder.isEnd()) {
            return (_currToken = null);
        }
        return (_currToken = _wrappedReader.readValue(_parser, _decoder));
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