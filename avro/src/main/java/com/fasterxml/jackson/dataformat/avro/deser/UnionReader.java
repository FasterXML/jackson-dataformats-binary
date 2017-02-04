package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.util.concurrent.Callable;

import org.apache.avro.io.ResolvingDecoder;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Reader used in cases where union contains at least one non-scalar type.
 */
final class UnionReader extends AvroStructureReader
{
    private AvroStructureReader[] _memberReaders;
    private final Callable<AvroStructureReader[]> _memberReadersProducer;
    private final ResolvingDecoder _decoder;
    private final AvroParserImpl _parser;

    public UnionReader(Callable<AvroStructureReader[]> supplier) {
        this(null, supplier, null, null);
    }

    private UnionReader(AvroReadContext parent,
    		Callable<AvroStructureReader[]> supplier,
            ResolvingDecoder decoder, AvroParserImpl parser)
    {
        super(parent, TYPE_ROOT);
        _memberReadersProducer = supplier;
        _decoder = decoder;
        _parser = parser;
    }
    
    @Override
    public UnionReader newReader(AvroReadContext parent,
            AvroParserImpl parser, ResolvingDecoder decoder) {
        return new UnionReader(parent, new Callable<AvroStructureReader[]>() {
			@Override
			public AvroStructureReader[] call() throws Exception {
				return memberReaders();
			}
		}, decoder, parser);
    }

    @Override
    public JsonToken nextToken() throws IOException
    {
        int index = _decoder.readIndex();
        AvroStructureReader[] memberReaders = memberReaders();
		if (index < 0 || index >= memberReaders.length) {
            throw new JsonParseException(_parser, String.format
                    ("Invalid index (%s); union only has %d types", index, memberReaders.length));
        }
        // important: remember to create new instance
        // also: must pass our parent (not this instance)
        AvroStructureReader reader = memberReaders[index].newReader(_parent, _parser, _decoder);
        return (_currToken = reader.nextToken());
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
    
    private AvroStructureReader[] memberReaders() {
    	if (_memberReaders != null) {
    		return _memberReaders;
    	}
    	try {
			_memberReaders = _memberReadersProducer.call();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
    	return _memberReaders;
    }
}
