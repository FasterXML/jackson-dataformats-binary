package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonToken;

public abstract class ArrayReader extends AvroStructureReader
{
    protected final static int STATE_START = 0;
    protected final static int STATE_ELEMENTS = 1;
    protected final static int STATE_END = 2;
    protected final static int STATE_DONE = 3;

    protected final AvroParserImpl _parser;
    protected final String _elementTypeId;

    protected int _state;
    protected long _count;

    protected String _currentName;
    
    protected ArrayReader(AvroReadContext parent, AvroParserImpl parser, String typeId, String elementTypeId)
    {
        super(parent, TYPE_ARRAY, typeId);
        _parser = parser;
        _elementTypeId = elementTypeId;
    }

    public static ArrayReader construct(ScalarDecoder reader, String typeId, String elementTypeId) {
        return new Scalar(reader, typeId, elementTypeId);
    }

    public static ArrayReader construct(AvroStructureReader reader, String typeId, String elementTypeId) {
        return new NonScalar(reader, typeId, elementTypeId);
    }

    @Override
    public String nextFieldName() throws IOException {
        nextToken();
        return null;
    }
    
    @Override
    public String getCurrentName() {
        if (_currentName == null) {
            _currentName = _parent.getCurrentName();
        }
        return _currentName;
    }

    @Override
    protected void appendDesc(StringBuilder sb) {
        sb.append('[');
        sb.append(getCurrentIndex());
        sb.append(']');
    }

    @Override
    public long getRemainingElements() {
        return _count - _index;
    }

    @Override
    public String getTypeId() {
        return _currToken != JsonToken.START_ARRAY && _currToken != JsonToken.END_ARRAY ? _elementTypeId : super.getTypeId();
    }

    /*
    /**********************************************************************
    /* Reader implementations for Avro arrays
    /**********************************************************************
     */

    private final static class Scalar extends ArrayReader
    {
        private final ScalarDecoder _elementReader;
        
        public Scalar(ScalarDecoder reader, String typeId, String elementTypeId) {
            this(null, reader, null, typeId, elementTypeId != null ? elementTypeId : reader.getTypeId());
        }

        private Scalar(AvroReadContext parent, ScalarDecoder reader, 
                AvroParserImpl parser, String typeId, String elementTypeId) {
            super(parent, parser, typeId, elementTypeId != null ? elementTypeId : reader.getTypeId());
            _elementReader = reader;
        }
        
        @Override
        public Scalar newReader(AvroReadContext parent, AvroParserImpl parser) {
            return new Scalar(parent, _elementReader, parser, _typeId, _elementTypeId);
        }

        @Override
        public JsonToken nextToken() throws IOException
        {
            switch (_state) {
            case STATE_START:
                _parser.setAvroContext(this);
                _index = 0;
                _count = _parser.decodeArrayStart();
                _state = (_count > 0) ? STATE_ELEMENTS : STATE_END;
                return (_currToken = JsonToken.START_ARRAY);
            case STATE_ELEMENTS:
                if (_index < _count) {
                    break;
                }
                if ((_count = _parser.decodeArrayNext()) > 0L) { // got more data
                    _index = 0;
                    break;
                }
                // otherwise, we are done: fall through
            case STATE_END:
                _state = STATE_DONE;
                _parser.setAvroContext(getParent());
                return (_currToken = JsonToken.END_ARRAY);
            case STATE_DONE:
            default:
                throwIllegalState(_state);
                return null;
            }

            // all good, just need to read the element value:
            ++_index;
            JsonToken t = _elementReader.decodeValue(_parser);
            _currToken = t;
            return t;
        }

        @Override
        public void skipValue(AvroParserImpl parser) throws IOException {
            // As per Avro spec/ref impl suggestion:
            long l;
            while ((l = parser.skipArray()) > 0L) {
                while (--l >= 0) {
                    _elementReader.skipValue(parser);
                }
            }
        }
    }

    private final static class NonScalar extends ArrayReader
    {
        private final AvroStructureReader _elementReader;
        
        public NonScalar(AvroStructureReader reader, String typeId, String elementTypeId) {
            this(null, reader, null, typeId, elementTypeId);
        }

        private NonScalar(AvroReadContext parent,
                AvroStructureReader reader, 
                AvroParserImpl parser, String typeId, String elementTypeId) {
            super(parent, parser, typeId, elementTypeId);
            _elementReader = reader;
        }
        
        @Override
        public NonScalar newReader(AvroReadContext parent,
                AvroParserImpl parser) {
            return new NonScalar(parent, _elementReader, parser, _typeId, _elementTypeId);
        }

        @Override
        public JsonToken nextToken() throws IOException
        {
            switch (_state) {
            case STATE_START:
                _parser.setAvroContext(this);
                _count = _parser.decodeArrayStart();
                _state = (_count > 0) ? STATE_ELEMENTS : STATE_END;
                return (_currToken = JsonToken.START_ARRAY);
            case STATE_ELEMENTS:
                if (_index < _count) {
                    break;
                }
                if ((_count = _parser.decodeArrayNext()) > 0L) { // got more data
                    _index = 0;
                    break;
                }
                // otherwise, we are done: fall through
            case STATE_END:
                _state = STATE_DONE;
                _parser.setAvroContext(getParent());
                return (_currToken = JsonToken.END_ARRAY);
            case STATE_DONE:
            default:
                throwIllegalState(_state);
            }
            ++_index;
            AvroStructureReader r = _elementReader.newReader(this, _parser);
            _parser.setAvroContext(r);
            return (_currToken = r.nextToken());
        }

        @Override
        public void skipValue(AvroParserImpl parser) throws IOException {
            // As per Avro spec/ref impl suggestion:
            long l;
            while ((l = parser.skipArray()) > 0L) {
                while (--l >= 0) {
                    _elementReader.skipValue(parser);
                }
            }
        }
    }
}