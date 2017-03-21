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

    public ScalarDecoderWrapper(ScalarDecoder wrappedReader) {
        this(null, null, wrappedReader);
    }

    private ScalarDecoderWrapper(AvroReadContext parent,
            AvroParserImpl parser, ScalarDecoder valueDecoder)
    {
        super(parent, TYPE_ROOT, null);
        _valueDecoder = valueDecoder;
        _parser = parser;
    }

    @Override
    public ScalarDecoderWrapper newReader(AvroReadContext parent, AvroParserImpl parser) {
        return new ScalarDecoderWrapper(parent, parser, _valueDecoder);
    }

    @Override
    public JsonToken nextToken() throws IOException
    {
        _parser.setAvroContext(getParent());
        return (_currToken = _valueDecoder.decodeValue(_parser));
    }

    @Override
    public String getTypeId() {
        return _valueDecoder.getTypeId();
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