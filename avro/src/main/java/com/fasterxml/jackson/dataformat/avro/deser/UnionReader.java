package com.fasterxml.jackson.dataformat.avro.deser;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.avro.Schema;

import java.io.IOException;

/**
 * Reader used in cases where union contains at least one non-scalar type.
 */
final class UnionReader extends AvroStructureReader
{
    private final AvroStructureReader[] _memberReaders;
    private final AvroParserImpl _parser;

    public UnionReader(AvroStructureReader[] memberReaders, Schema schema) {
        this(null, memberReaders, null, schema);
    }

    private UnionReader(AvroReadContext parent,
            AvroStructureReader[] memberReaders, AvroParserImpl parser, Schema schema)
    {
        super(parent, TYPE_ROOT, schema);
        _memberReaders = memberReaders;
        _parser = parser;
    }
    
    @Override
    public UnionReader newReader(AvroReadContext parent, AvroParserImpl parser) {
        return new UnionReader(parent, _memberReaders, parser, _schema);
    }

    @Override
    public JsonToken nextToken() throws IOException
    {
        int index = _decodeIndex();
        // important: remember to create new instance
        // also: must pass our parent (not this instance)
        AvroStructureReader reader = _memberReaders[index].newReader(_parent, _parser);
        return (_currToken = reader.nextToken());
    }

    @Override
    public void skipValue(AvroParserImpl parser) throws IOException {
        int index = _decodeIndex();
        // NOTE: no need to create new instance since it's stateless call and
        // we pass decoder to use
        _memberReaders[index].skipValue(parser);
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
        int index = _parser.decodeIndex();
        if (index < 0 || index >= _memberReaders.length) {
            throw new JsonParseException(_parser, String.format
                    ("Invalid index (%s); union only has %d types", index, _memberReaders.length));
        }
        return index;
    }
}
