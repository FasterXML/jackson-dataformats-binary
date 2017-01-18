package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.io.BinaryDecoder;

import com.fasterxml.jackson.core.JsonToken;

final class RecordReader extends AvroStructureReader
{
    protected final static int STATE_START = 0;
    protected final static int STATE_NAME = 1;
    protected final static int STATE_VALUE = 2;
    protected final static int STATE_END = 3;
    protected final static int STATE_DONE = 4;

    private final AvroFieldWrapper[] _fieldReaders;
    private final BinaryDecoder _decoder;
    private final AvroParserImpl _parser;

    protected String _currentName;

    protected int _state;
    protected final int _count;

    public RecordReader(AvroFieldWrapper[] fieldReaders) {
        this(null, fieldReaders, null, null);
    }

    private RecordReader(AvroReadContext parent,
            AvroFieldWrapper[] fieldReaders,
            BinaryDecoder decoder, AvroParserImpl parser)
    {
        super(parent, TYPE_OBJECT);
        _fieldReaders = fieldReaders;
        _decoder = decoder;
        _parser = parser;
        _count = fieldReaders.length;
    }

    @Override
    public RecordReader newReader(AvroReadContext parent,
            AvroParserImpl parser, BinaryDecoder decoder) {
        return new RecordReader(parent, _fieldReaders, decoder, parser);
    }

    @Override
    public String getCurrentName() { return _currentName; }

    @Override
    public JsonToken nextToken() throws IOException
    {
        switch (_state) {
        case STATE_START:
            _parser.setAvroContext(this);
            _state = (_count > 0) ? STATE_NAME : STATE_END;
            {
                JsonToken t = JsonToken.START_OBJECT;
                _currToken = t;
                return t;
            }
        case STATE_NAME:
            if (_index >= _count) {
                return _nextAtEndObject();
            }
            _currentName = _fieldReaders[_index].getName();
            _state = STATE_VALUE;
            {
                JsonToken t = JsonToken.FIELD_NAME;
                _currToken = t;
                return t;
            }
        case STATE_VALUE:
            break;
        case STATE_END:
            return _nextAtEndObject();
        case STATE_DONE:
        default:
            throwIllegalState(_state);
        }
        _state = STATE_NAME;
        AvroFieldWrapper field = _fieldReaders[_index];
        ++_index;
        JsonToken t = field.readValue(this, _parser, _decoder);
        _currToken = t;
        return t;
    }        

    private final JsonToken _nextAtEndObject() throws IOException
    {
        AvroReadContext parent = getParent();
        // as per [dataformats-binary#38], may need to reset, instead of bailing out
        if (parent.inRoot()) {
            if (!_decoder.isEnd()) {
                _state = STATE_START;
                _index = 0;
                return (_currToken = JsonToken.END_OBJECT);
            }
        }
        _state = STATE_DONE;
        _parser.setAvroContext(getParent());
        return (_currToken = JsonToken.END_OBJECT);
    }

    @Override
    public String nextFieldName() throws IOException
    {
        if (_state == STATE_NAME) {
            if (_index < _count) {
                String name = _fieldReaders[_index].getName();
                _currentName = name;
                _state = STATE_VALUE;
                _currToken = JsonToken.FIELD_NAME;
                return name;
            }
            _nextAtEndObject();
        } else {
            nextToken();
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