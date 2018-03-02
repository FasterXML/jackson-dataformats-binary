package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonToken;

/**
 * Context used at root level; basically just a container
 * over actual {@link AvroStructureReader}.
 */
public class RootReader extends AvroReadContext
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
        return _valueReader.newReader(this, _parser).nextToken();
    }

    @Override
    public void skipValue(AvroParserImpl parser) throws IOException {
        throw new UnsupportedOperationException();
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
        // Could create etc, but realistically no names at root level so:
        return null;
//        return _valueReader.newReader(this, _parser).nextFieldName();
    }
    
    @Override
    public String getTypeId() {
        return _valueReader.getTypeId();
    }
}
