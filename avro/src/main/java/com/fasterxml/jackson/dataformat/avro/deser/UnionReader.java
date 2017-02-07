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
        int index = _decodeIndex();
        // important: remember to create new instance
        // also: must pass our parent (not this instance)
        AvroStructureReader reader = _memberReaders[index].newReader(_parent, _parser, _decoder);
        return (_currToken = reader.nextToken());
    }

    @Override
    public void skipValue(BinaryDecoder decoder) throws IOException {
        int index = _decodeIndex();
        // NOTE: no need to create new instance since it's stateless call and
        // we pass decoder to use
        _memberReaders[index].skipValue(decoder);
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

    private final int _decodeIndex() throws IOException {
        int index = _decoder.readIndex();
        if (index < 0 || index >= _memberReaders.length) {
            throw new JsonParseException(_parser, String.format
                    ("Invalid index (%s); union only has %d types", index, _memberReaders.length));
        }
        return index;
    }
}
