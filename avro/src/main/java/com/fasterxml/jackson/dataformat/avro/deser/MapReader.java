package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonToken;

public abstract class MapReader extends AvroStructureReader
{
    protected final static int STATE_START = 0;
    protected final static int STATE_NAME = 1;
    protected final static int STATE_VALUE = 2;
    protected final static int STATE_END = 3;
    protected final static int STATE_DONE = 4;

    protected final AvroParserImpl _parser;
    protected final String _keyTypeId;
    protected final String _valueTypeId;

    protected String _currentName;

    protected int _state;
    
    protected MapReader(String typeId, String keyTypeId, String valueTypeId) {
        this(null, null, typeId, keyTypeId, valueTypeId);
    }

    protected MapReader(AvroReadContext parent, AvroParserImpl parser, String typeId, String keyTypeId, String valueTypeId) {
        super(parent, TYPE_OBJECT, typeId);
        _parser = parser;
        _keyTypeId = keyTypeId;
        _valueTypeId = valueTypeId;
    }

    public static MapReader construct(ScalarDecoder dec, String typeId, String keyTypeId, String valueTypeId) {
        return new Scalar(dec, typeId, keyTypeId, valueTypeId);
    }

    public static MapReader construct(AvroStructureReader reader, String typeId, String keyTypeId) {
        return new NonScalar(reader, typeId, keyTypeId);
    }

    @Override
    public abstract MapReader newReader(AvroReadContext parent, AvroParserImpl parser);

    @Override
    public String getCurrentName() { return _currentName; }

    @Override
    public abstract JsonToken nextToken() throws IOException;
    
    @Override
    public abstract void skipValue(AvroParserImpl parser) throws IOException;

    @Override
    public abstract long getRemainingElements();

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

    @Override
    public String getTypeId() {
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.END_OBJECT) {
            return super.getTypeId();
        }
        if (_currToken == JsonToken.FIELD_NAME && _state == STATE_VALUE) {
            return _keyTypeId;
        }
        return _valueTypeId;
    }

    /*
    /**********************************************************************
    /* Implementations
    /**********************************************************************
     */

    private final static class Scalar extends MapReader
    {
        private final ScalarDecoder _scalarDecoder;
        protected long _count;

        protected Scalar(ScalarDecoder dec, String typeId, String keyTypeId, String valueTypeId) {
            super(typeId, keyTypeId, valueTypeId != null ? valueTypeId : dec.getTypeId());
            _scalarDecoder = dec;
        }

        protected Scalar(AvroReadContext parent,
                AvroParserImpl parser, ScalarDecoder sd, String typeId, String keyTypeId, String valueTypeId) {
            super(parent, parser, typeId, keyTypeId, valueTypeId != null ? valueTypeId : sd.getTypeId());
            _scalarDecoder = sd;
        }

        @Override
        public long getRemainingElements() {
            return _count - _index;
        }
        
        @Override
        public MapReader newReader(AvroReadContext parent, AvroParserImpl parser) {
            return new Scalar(parent, parser, _scalarDecoder, _typeId, _keyTypeId, _valueTypeId);
        }

        @Override
        public JsonToken nextToken() throws IOException
        {
            switch (_state) {
            case STATE_START:
                _parser.setAvroContext(this);
                _count = _parser.decodeMapStart();
                _state = (_count > 0) ? STATE_NAME : STATE_END;
                return (_currToken = JsonToken.START_OBJECT);
            case STATE_NAME:
                if (_index < _count) {
                    _state = STATE_VALUE;
                    _currentName = _parser.decodeMapKey();
                    return (_currToken = JsonToken.FIELD_NAME);
                }
                // need more data...
                _count = _parser.decodeMapNext();
                // more stuff?
                if (_count > 0L) {
                    _index = 0;
                    _currentName = _parser.decodeMapKey();
                    return (_currToken = JsonToken.FIELD_NAME);
                }
                // otherwise fall through:
            case STATE_END:
                _state = STATE_DONE;
                _parser.setAvroContext(getParent());
                return (_currToken = JsonToken.END_OBJECT);
            case STATE_VALUE:
                break;
            case STATE_DONE:
            default:
                throwIllegalState(_state);
            }
            _state = STATE_NAME;
            ++_index;
            return _scalarDecoder.decodeValue(_parser);
        }

        @Override
        public void skipValue(AvroParserImpl parser) throws IOException {
            // As per Avro spec/ref impl suggestion:
            long l;
            while ((l = parser.skipMap()) > 0L) {
                while (--l >= 0) {
                    _scalarDecoder.skipValue(parser);
                }
            }
        }
    }

    private final static class NonScalar extends MapReader
    {
        private final AvroStructureReader _structureReader;
        protected long _count;

        public NonScalar(AvroStructureReader reader, String typeId, String keyTypeId) {
            super(typeId, keyTypeId, null);
            _structureReader = reader;
        }

        public NonScalar(AvroReadContext parent,
                AvroParserImpl parser, AvroStructureReader reader, String typeId, String keyTypeId) {
            super(parent, parser, typeId, keyTypeId, null);
            _structureReader = reader;
        }

        @Override
        public long getRemainingElements() {
            return _count - _index;
        }
        
        @Override
        public MapReader newReader(AvroReadContext parent, AvroParserImpl parser) {
            return new NonScalar(parent, parser, _structureReader, _typeId, _keyTypeId);
        }

        @Override
        public JsonToken nextToken() throws IOException
        {
            switch (_state) {
            case STATE_START:
                _parser.setAvroContext(this);
                _count = _parser.decodeMapStart();
                _state = (_count > 0) ? STATE_NAME : STATE_END;
                return (_currToken = JsonToken.START_OBJECT);
            case STATE_NAME:
                if (_index < _count) {
                    _state = STATE_VALUE;
                    _currentName = _parser.decodeMapKey();
                    return (_currToken = JsonToken.FIELD_NAME);
                }
                // need more data...
                _count = _parser.decodeMapNext();
                // more stuff?
                if (_count > 0L) {
                    _index = 0;
                    _currentName = _parser.decodeMapKey();
                    return (_currToken = JsonToken.FIELD_NAME);
                }
                // otherwise fall through:
            case STATE_END:
                _state = STATE_DONE;
                _parser.setAvroContext(getParent());
                return (_currToken = JsonToken.END_OBJECT);
            case STATE_VALUE:
                break;
            case STATE_DONE:
            default:
                throwIllegalState(_state);
            }
            _state = STATE_NAME;
            ++_index;
            AvroStructureReader r = _structureReader.newReader(this, _parser);
            _parser.setAvroContext(r);
            return (_currToken = r.nextToken());
        }

        @Override
        public void skipValue(AvroParserImpl parser) throws IOException {
            // As per Avro spec/ref impl suggestion:
            long l;
            while ((l = parser.skipMap()) > 0L) {
                while (--l >= 0) {
                    _structureReader.skipValue(parser);
                }
            }
        }
    }
}