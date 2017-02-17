package com.fasterxml.jackson.dataformat.avro.deser;

import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;

/**
 * Context used at root level; basically just a container
 * over actual {@link AvroStructureReader}.
 */
public final class RootReader extends AvroReadContext
{
    private final AvroParserImpl _parser;
    private final AvroStructureReader _valueReader;

    public RootReader(AvroParserImpl parser,
            AvroStructureReader valueReader) {
        super(null, null);
        _type = TYPE_ROOT;
        _parser = parser;
        _valueReader = valueReader;
    }

    @Override
    public JsonToken nextToken() throws IOException {
        // First: possibly we are at end. Could theoretically check against
        // empty streams but...
        if (_parser.checkInputEnd()) {
            return null;
        }
        AvroStructureReader r = _valueReader.newReader(this, _parser);
        return r.nextToken();
    }

    @Override
    public JsonToken getCurrentToken() {
        return null;
    }

    @Override
    public void appendDesc(StringBuilder sb) {
        sb.append("/");
    }

    @Override
    public String nextFieldName() throws IOException {
        AvroStructureReader r = _valueReader.newReader(this, _parser);
        return r.nextFieldName();
    }
}
