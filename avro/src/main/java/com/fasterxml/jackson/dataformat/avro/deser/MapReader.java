package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;

import com.fasterxml.jackson.core.JsonToken;

public abstract class MapReader extends AvroStructureReader
{
    protected final static int STATE_START = 0;
    protected final static int STATE_NAME = 1;
    protected final static int STATE_VALUE = 2;
    protected final static int STATE_END = 3;
    protected final static int STATE_DONE = 4;

    protected final AvroParserImpl _parser;
    protected final Schema _keySchema;

    protected String _currentName;

    protected int _state;
    
    protected MapReader(Schema schema) {
        this(null, null, schema);
    }

    protected MapReader(AvroReadContext parent, AvroParserImpl parser, Schema schema) {
        super(parent, TYPE_OBJECT, schema);
        _parser = parser;
        _keySchema = Schema.create(Schema.Type.STRING);
        if (schema.getProp(SpecificData.KEY_CLASS_PROP) != null) {
            _keySchema.addProp(SpecificData.CLASS_PROP, schema.getProp(SpecificData.KEY_CLASS_PROP));
        }
    }

    public static MapReader construct(ScalarDecoder dec, Schema schema) {
        return new Scalar(dec, schema);
    }

    public static MapReader construct(AvroStructureReader reader, Schema schema) {
        return new NonScalar(reader, schema);
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

    @Override
    public Schema getSchema() {
        if (_currToken == JsonToken.FIELD_NAME) {
            return _keySchema;
        }
        if (_currToken != JsonToken.START_OBJECT && _currToken != JsonToken.END_OBJECT) {
            return _schema.getValueType();
        }
        return super.getSchema();
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

        protected Scalar(ScalarDecoder dec, Schema schema) {
            super(schema);
            _scalarDecoder = dec;
        }

        protected Scalar(AvroReadContext parent,
                AvroParserImpl parser, ScalarDecoder sd, Schema schema) {
            super(parent, parser, schema);
            _scalarDecoder = sd;
        }
        
        @Override
        public MapReader newReader(AvroReadContext parent, AvroParserImpl parser) {
            return new Scalar(parent, parser, _scalarDecoder, _schema);
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

        public NonScalar(AvroStructureReader reader, Schema schema) {
            super(schema);
            _structureReader = reader;
        }

        public NonScalar(AvroReadContext parent,
                AvroParserImpl parser, AvroStructureReader reader, Schema schema) {
            super(parent, parser, schema);
            _structureReader = reader;
        }
        
        @Override
        public MapReader newReader(AvroReadContext parent, AvroParserImpl parser) {
            return new NonScalar(parent, parser, _structureReader, _schema);
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