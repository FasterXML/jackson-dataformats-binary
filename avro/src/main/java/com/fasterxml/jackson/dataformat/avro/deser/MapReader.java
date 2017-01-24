package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.io.BinaryDecoder;

import com.fasterxml.jackson.core.JsonToken;

public final class MapReader extends AvroStructureReader
{
    protected final static int STATE_START = 0;
    protected final static int STATE_NAME = 1;
    protected final static int STATE_VALUE = 2;
    protected final static int STATE_END = 3;
    protected final static int STATE_DONE = 4;

    private final AvroScalarReader _scalarReader;
    private final AvroStructureReader _structureReader;
    protected final BinaryDecoder _decoder;
    protected final AvroParserImpl _parser;

    private String _currentName;

    protected int _state;
    protected long _count;
    
    public MapReader(AvroScalarReader reader) {
        this(null, reader, null, null, null);
    }

    public MapReader(AvroStructureReader reader) {
        this(null, null, reader, null, null);
    }
    
    private MapReader(AvroReadContext parent,
            AvroScalarReader scalarReader,
            AvroStructureReader structReader,
            BinaryDecoder decoder, AvroParserImpl parser) {
        super(parent, TYPE_OBJECT);
        _scalarReader = scalarReader;
        _structureReader = structReader;
        _decoder = decoder;
        _parser = parser;
    }
    
    @Override
    public MapReader newReader(AvroReadContext parent,
            AvroParserImpl parser, BinaryDecoder decoder) {
        return new MapReader(parent, _scalarReader, _structureReader, decoder, parser);
    }

    @Override
    public String getCurrentName() { return _currentName; }
    
    @Override
    public JsonToken nextToken() throws IOException
    {
        switch (_state) {
        case STATE_START:
            _parser.setAvroContext(this);
            _count = _decoder.readMapStart();
            _state = (_count > 0) ? STATE_NAME : STATE_END;
            return (_currToken = JsonToken.START_OBJECT);
        case STATE_NAME:
            if (_index < _count) {
                _state = STATE_VALUE;
                _currentName = _decoder.readString();
                return (_currToken = JsonToken.FIELD_NAME);
            }
            // need more data...
            _count = _decoder.mapNext();
            // more stuff?
            if (_count > 0L) {
                _index = 0;
                _currentName = _decoder.readString();
                return (_currToken = JsonToken.FIELD_NAME);
            }
            // otherwise fall through:
        case STATE_END:
            final AvroReadContext parent = getParent();
            // as per [dataformats-binary#38], may need to reset, instead of bailing out
            // ... note, however, that we can't as of yet test it, alas.
            if (parent.inRoot()) {
                if (!_decoder.isEnd()) {
                    _index = 0;
                    _state = STATE_START;
                    return (_currToken = JsonToken.END_OBJECT);
                }
            }
            _state = STATE_DONE;
            _parser.setAvroContext(parent);
            return (_currToken = JsonToken.END_OBJECT);
        case STATE_VALUE:
            break;
        case STATE_DONE:
        default:
            throwIllegalState(_state);
        }
        _state = STATE_NAME;
        ++_index;
        if (_scalarReader != null) {
            return _scalarReader.readValue(_parser, _decoder);
        }
        AvroStructureReader r = _structureReader.newReader(this, _parser, _decoder);
        _parser.setAvroContext(r);
        return (_currToken = r.nextToken());
    }

    @Override
    public String nextFieldName() throws IOException {
        JsonToken t = nextToken();
        if (t == JsonToken.FIELD_NAME) {
            return _currentName;
        }
        return null;
    }

    @Override
    public void appendDesc(StringBuilder sb)
    {
        sb.append('{');
        if (_currentName != null) {
            sb.append('"');
            sb.append(_currentName);
            sb.append('"');
        } else {
            sb.append('?');
        }
        sb.append('}');
    }
}