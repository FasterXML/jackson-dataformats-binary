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

    protected String _currentName;

    protected int _state;
    protected long _count;
    
    protected MapReader() {
        this(null, null);
    }

    protected MapReader(AvroReadContext parent, AvroParserImpl parser) {
        super(parent, TYPE_OBJECT);
        _parser = parser;
    }

    public static MapReader construct(ScalarDecoder dec) {
        return new Scalar(dec);
    }

    public static MapReader construct(AvroStructureReader reader) {
        return new NonScalar(reader);
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

    /*
    /**********************************************************************
    /* Implementations
    /**********************************************************************
     */

    private final static class Scalar extends MapReader
    {
        private final ScalarDecoder _scalarDecoder;

        protected Scalar(ScalarDecoder dec) {
            _scalarDecoder = dec;
        }

        protected Scalar(AvroReadContext parent,
                AvroParserImpl parser, ScalarDecoder sd) {
            super(parent, parser);
            _scalarDecoder = sd;
        }
        
        @Override
        public MapReader newReader(AvroReadContext parent, AvroParserImpl parser) {
            return new Scalar(parent, parser, _scalarDecoder);
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
                final AvroReadContext parent = getParent();
                // as per [dataformats-binary#38], may need to reset, instead of bailing out
                if (parent.inRoot()) {
                    if (!_parser.checkInputEnd()) {
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

        public NonScalar(AvroStructureReader reader) {
            _structureReader = reader;
        }

        public NonScalar(AvroReadContext parent,
                AvroParserImpl parser, AvroStructureReader reader) {
            super(parent, parser);
            _structureReader = reader;
        }
        
        @Override
        public MapReader newReader(AvroReadContext parent, AvroParserImpl parser) {
            return new NonScalar(parent, parser, _structureReader);
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
                final AvroReadContext parent = getParent();
                // as per [dataformats-binary#38], may need to reset, instead of bailing out
                if (parent.inRoot()) {
                    if (!_parser.checkInputEnd()) {
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