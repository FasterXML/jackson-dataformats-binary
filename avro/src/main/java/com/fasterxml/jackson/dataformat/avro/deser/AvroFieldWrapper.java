package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.io.BinaryDecoder;

import com.fasterxml.jackson.core.JsonToken;

public final class AvroFieldWrapper
{
    protected final String _name;
    protected final AvroScalarReader _scalarReader;
    protected final AvroStructureReader _structureReader;

    public AvroFieldWrapper(String name, AvroScalarReader scalarReader) {
        _name = name;
        _scalarReader = scalarReader;
        _structureReader = null;
    }

    public AvroFieldWrapper(String name, AvroStructureReader structureReader) {
        _name = name;
        _structureReader = structureReader;
        _scalarReader = null;
    }

    public String getName() { return _name; }

    public JsonToken readValue(AvroReadContext parent,
            AvroParserImpl parser, BinaryDecoder decoder)
        throws IOException
    {
        if (_scalarReader != null) {
            return _scalarReader.readValue(parser, decoder);
        }
        return  _structureReader.newReader(parent, parser, decoder)
                .nextToken();
    }
}
