package com.fasterxml.jackson.dataformat.avro.deser;

import com.fasterxml.jackson.core.JsonToken;
import org.apache.avro.Schema;

import java.io.IOException;

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

    public ScalarDecoderWrapper(ScalarDecoder wrappedReader, Schema schema) {
        this(null, null, wrappedReader, schema);
    }

    private ScalarDecoderWrapper(AvroReadContext parent,
            AvroParserImpl parser, ScalarDecoder valueDecoder, Schema schema)
    {
        super(parent, TYPE_ROOT, schema);
        _valueDecoder = valueDecoder;
        _parser = parser;
    }

    @Override
    public ScalarDecoderWrapper newReader(AvroReadContext parent, AvroParserImpl parser) {
        return new ScalarDecoderWrapper(parent, parser, _valueDecoder, _schema);
    }

    @Override
    public JsonToken nextToken() throws IOException
    {
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