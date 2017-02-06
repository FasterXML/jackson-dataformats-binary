package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.io.Decoder;

import com.fasterxml.jackson.core.JsonToken;

final class ScalarReaderWrapper extends AvroStructureReader
{
    /**
     * Actual decoder used to decode scalar value, wrapped by this reader.
     */
    private final AvroScalarDecoder _valueDecoder;

    private final Decoder _decoder;
    private final AvroParserImpl _parser;
    private final boolean _rootReader;

    public ScalarReaderWrapper(AvroScalarDecoder wrappedReader) {
        this(null, null, null, wrappedReader, false);
    }

    private ScalarReaderWrapper(AvroReadContext parent,
            AvroParserImpl parser, Decoder decoder,
            AvroScalarDecoder valueDecoder, boolean rootReader)
    {
        super(parent, TYPE_ROOT);
        _valueDecoder = valueDecoder;
        _parser = parser;
        _decoder = decoder;
        _rootReader = rootReader;
    }

    @Override
    public ScalarReaderWrapper newReader(AvroReadContext parent,
            AvroParserImpl parser, Decoder decoder) {
        return new ScalarReaderWrapper(parent, parser, decoder, _valueDecoder, parent.inRoot());
    }

    @Override
    public JsonToken nextToken() throws IOException
    {
        // 19-Jan-2017, tatu: May need to be called multiple times, for root-level
        //    sequences. Because of this need to check for EOF. But only after reading
        //    one token successfully...
        if (_rootReader) {
            JsonToken t = DecodeUtil.isEnd(_decoder) ? null : _valueDecoder.decodeValue(_parser, _decoder);
            return (_currToken = t);
        }
        _parser.setAvroContext(getParent());
        return (_currToken = _valueDecoder.decodeValue(_parser, _decoder));
    }

    @Override
    public void skipValue(Decoder decoder) throws IOException {
        _valueDecoder.skipValue(decoder);
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