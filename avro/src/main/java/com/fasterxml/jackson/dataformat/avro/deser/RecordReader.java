package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.util.concurrent.Callable;

import org.apache.avro.io.ResolvingDecoder;

import com.fasterxml.jackson.core.JsonToken;

final class RecordReader extends AvroStructureReader
{
    protected final static int STATE_START = 0;
    protected final static int STATE_NAME = 1;
    protected final static int STATE_VALUE = 2;
    protected final static int STATE_END = 3;
    protected final static int STATE_DONE = 4;

    private   AvroFieldWrapper[] _fieldReaders;
    private final Callable<AvroFieldWrapper[]> _supplier;
    private final ResolvingDecoder _decoder;
    private final AvroParserImpl _parser;

    protected String _currentName;

    protected int _state;
    protected   int _count;

    public RecordReader(Callable<AvroFieldWrapper[]> supplier) {
        this(null, supplier, null, null);
    }

    private RecordReader(AvroReadContext parent,
    		Callable<AvroFieldWrapper[]> supplier,
            ResolvingDecoder decoder, AvroParserImpl parser)
    {
        super(parent, TYPE_OBJECT); 
        _decoder = decoder;
        _parser = parser;
        _supplier = supplier;
    }

    @Override
	public RecordReader newReader(AvroReadContext parent, AvroParserImpl parser, ResolvingDecoder decoder)
    {
		return new RecordReader(parent, new Callable<AvroFieldWrapper[]>() {
			@Override
			public AvroFieldWrapper[] call() throws Exception {
				return fieldReaders();
			}
		}, decoder, parser);
	}

    @Override
    public String getCurrentName() { return _currentName; }

    @Override
    public JsonToken nextToken() throws IOException
    {
        switch (_state) {
        case STATE_START:
        	fieldReaders();
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
        	if (!_parser.isEnd()) {
				_state = STATE_START;
				_index = 0;
				_decoder.drain();
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
                String name = fieldReaders()[_index].getName();
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
    
    private AvroFieldWrapper[] fieldReaders()
    {
    	if (_fieldReaders != null) {
    		return _fieldReaders;
    	}
    	try {
			_fieldReaders = _supplier.call();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
        _count = _fieldReaders.length;
        return _fieldReaders;
    }
}