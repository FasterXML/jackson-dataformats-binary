package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonToken;

/**
 * Simple adapter needed in some cases to unify handling of reading (and
 * skipping) of structured and scalar values.
 */
final class ScalarDecoderWrapper extends AvroStructureReader
{
    /**
     * Actual decoder used to decode scalar value, wrapped by this reader.
     */
    private final ScalarDecoder _valueDecoder;

    private final AvroParserImpl _parser;
    private final boolean _rootReader;

    public ScalarDecoderWrapper(ScalarDecoder wrappedReader) {
        this(null, null, wrappedReader, false);
    }

    private ScalarDecoderWrapper(AvroReadContext parent,
            AvroParserImpl parser, ScalarDecoder valueDecoder,
            boolean rootReader)
    {
        super(parent, TYPE_ROOT);
        _valueDecoder = valueDecoder;
        _parser = parser;
        _rootReader = rootReader;
    }

    @Override
    public ScalarDecoderWrapper newReader(AvroReadContext parent, AvroParserImpl parser) {
        return new ScalarDecoderWrapper(parent, parser, _valueDecoder, parent.inRoot());
    }

    @Override
    public JsonToken nextToken() throws IOException
    {
        // 19-Jan-2017, tatu: May need to be called multiple times, for root-level
        //    sequences. Because of this need to check for EOF. But only after reading
        //    one token successfully...
        if (_rootReader) {
            JsonToken t = _parser.checkInputEnd() ? null : _valueDecoder.decodeValue(_parser);
            return (_currToken = t);
        }
        _parser.setAvroContext(getParent());
        return (_currToken = _valueDecoder.decodeValue(_parser));
    }

    @Override
    public void skipValue(AvroParserImpl parser) throws IOException {
        _valueDecoder.skipValue(parser);
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