package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.io.BinaryDecoder;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Reader used in cases where union contains at least one non-scalar type.
 */
final class UnionReader extends AvroStructureReader
{
    private final AvroStructureReader[] _memberReaders;
    private final BinaryDecoder _decoder;
    private final AvroParserImpl _parser;

    private AvroStructureReader _currentReader;
    
    public UnionReader(AvroStructureReader[] memberReaders) {
        this(null, memberReaders, null, null);
    }

    private UnionReader(AvroReadContext parent,
            AvroStructureReader[] memberReaders,
            BinaryDecoder decoder, AvroParserImpl parser)
    {
        super(parent, TYPE_ROOT);
        _memberReaders = memberReaders;
        _decoder = decoder;
        _parser = parser;
    }
    
    @Override
    public UnionReader newReader(AvroReadContext parent,
            AvroParserImpl parser, BinaryDecoder decoder) {
        return new UnionReader(parent, _memberReaders, decoder, parser);
    }

    @Override
    public JsonToken nextToken() throws IOException
    {
        if (_currentReader == null) {
            int index = _decoder.readIndex();
            if (index < 0 || index >= _memberReaders.length) {
                throw new JsonParseException("Invalid index ("+index+"); union only has "
                        +_memberReaders.length+" types",
                        _parser.getCurrentLocation());
            }
            // important: remember to create new instance
            // also: must pass our parent (not this instance)
            _currentReader = _memberReaders[index].newReader(_parent, _parser, _decoder);
        }
        JsonToken t = _currentReader.nextToken();
        _currToken = t;
        return t;
    }

    @Override
    public String nextFieldName() throws IOException {
        nextToken();
        return null;
    }
    
    @Override
    protected void appendDesc(StringBuilder sb) {
        sb.append('?');
    }
}