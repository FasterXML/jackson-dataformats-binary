package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.io.Decoder;
import org.apache.avro.io.ResolvingDecoder;

import com.fasterxml.jackson.core.JsonToken;

final class ScalarReaderWrapper extends AvroStructureReader
{
    private final AvroScalarReader _wrappedReader;
    private final Decoder _decoder;
    private final AvroParserImpl _parser;
    private final boolean _rootReader;

    public ScalarReaderWrapper(AvroScalarReader wrappedReader) {
        this(null, null, null, wrappedReader, false);
    }

    private ScalarReaderWrapper(AvroReadContext parent,
            AvroParserImpl parser, Decoder decoder,
            AvroScalarReader wrappedReader, boolean rootReader)
    {
        super(parent, TYPE_ROOT);
        _wrappedReader = wrappedReader;
        _parser = parser;
        _decoder = decoder;
        _rootReader = rootReader;
    }

    @Override
    public ScalarReaderWrapper newReader(AvroReadContext parent,
            AvroParserImpl parser, ResolvingDecoder decoder) {
        return new ScalarReaderWrapper(parent, parser, decoder, _wrappedReader, parent.inRoot());
    }

    @Override
    public JsonToken nextToken() throws IOException
    {
        // 19-Jan-2017, tatu: May need to be called multiple times, for root-level
        //    sequences. Because of this need to check for EOF. But only after reading
        //    one token successfully...
        if (_rootReader) {
            JsonToken t = DecodeUtil.isEnd(_decoder) ? null : _wrappedReader.readValue(_parser, _decoder);
            return (_currToken = t);
        }
        _parser.setAvroContext(getParent());
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