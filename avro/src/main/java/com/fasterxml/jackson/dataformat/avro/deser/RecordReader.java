package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.Schema;

import com.fasterxml.jackson.core.JsonToken;

abstract class RecordReader extends AvroStructureReader
{
    protected final static int STATE_START = 0;
    protected final static int STATE_NAME = 1;
    protected final static int STATE_VALUE = 2;
    protected final static int STATE_END = 3;
    protected final static int STATE_DONE = 4;

    protected final AvroFieldReader[] _fieldReaders;
    protected final AvroParserImpl _parser;

    protected String _currentName;

    protected int _state;
    protected final int _count;

    protected RecordReader(AvroReadContext parent,
            AvroFieldReader[] fieldReaders, AvroParserImpl parser, Schema schema)
    {
        super(parent, TYPE_OBJECT, schema);
        _fieldReaders = fieldReaders;
        _parser = parser;
        _count = fieldReaders.length;
    }

    @Override
    public abstract RecordReader newReader(AvroReadContext parent, AvroParserImpl parser);

    @Override
    public String getCurrentName() { return _currentName; }

    @Override
    public final void skipValue(AvroParserImpl parser) throws IOException {
        for (int i = 0, end = _fieldReaders.length; i < end; ++i) {
            _fieldReaders[i].skipValue(parser);
        }
    }

    protected final JsonToken _nextAtEndObject() throws IOException
    {
        _state = STATE_DONE;
        _parser.setAvroContext(getParent());
        return (_currToken = JsonToken.END_OBJECT);
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

    public final static class Std
        extends RecordReader
    {
        public Std(AvroFieldReader[] fieldReaders, Schema schema) {
            super(null, fieldReaders, null, schema);
        }

        public Std(AvroReadContext parent,
                AvroFieldReader[] fieldReaders, AvroParserImpl parser, Schema schema) {
            super(parent, fieldReaders, parser, schema);
        }
        
        @Override
        public RecordReader newReader(AvroReadContext parent,
                AvroParserImpl parser) {
            return new Std(parent, _fieldReaders, parser, _schema);
        }

        @Override
        public Schema getSchema() {
            if (_currToken == JsonToken.FIELD_NAME) {
                return super.getSchema().getFields().get(_index).schema();
            }
            if ( _state == STATE_NAME && _index <= _count && _currToken != JsonToken.START_OBJECT) {
                return super.getSchema().getFields().get(_index-1).schema();
            }
            return super.getSchema();
        }

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
                if (_index < _count) {
                    _currentName = _fieldReaders[_index].getName();
                    _state = STATE_VALUE;
                    {
                        JsonToken t = JsonToken.FIELD_NAME;
                        _currToken = t;
                        return t;
                    }
                }
                return _nextAtEndObject();
            case STATE_VALUE:
                break;
            case STATE_END:
                return _nextAtEndObject();
            case STATE_DONE:
            default:
                throwIllegalState(_state);
            }
            _state = STATE_NAME;
            AvroFieldReader field = _fieldReaders[_index];
            ++_index;
            JsonToken t = field.readValue(this, _parser);
            _currToken = t;
            return t;
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
    }

    public final static class Resolving
        extends RecordReader
    {
        public Resolving(AvroFieldReader[] fieldReaders, Schema schema) {
            super(null, fieldReaders, null, schema);
        }
        public Resolving(AvroReadContext parent,
                AvroFieldReader[] fieldReaders, AvroParserImpl parser, Schema schema) {
            super(parent, fieldReaders, parser, schema);
        }

        @Override
        public RecordReader newReader(AvroReadContext parent, AvroParserImpl parser) {
            return new Resolving(parent, _fieldReaders, parser, _schema);
        }

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
                while (_index < _count) {
                    AvroFieldReader r = _fieldReaders[_index];
                    if (r.isSkipper()) {
                        ++_index;
                        r.skipValue(_parser);
                        continue;
                    }
                    _currentName = r.getName();
                    _state = STATE_VALUE;
                    return (_currToken = JsonToken.FIELD_NAME);
                }
                return _nextAtEndObject();
            case STATE_VALUE:
                break;
            case STATE_END:
                return _nextAtEndObject();
            case STATE_DONE:
            default:
                throwIllegalState(_state);
            }
            _state = STATE_NAME;
            AvroFieldReader field = _fieldReaders[_index];
            ++_index;
            JsonToken t = field.readValue(this, _parser);
            _currToken = t;
            return t;
        }

        @Override
        public String nextFieldName() throws IOException
        {
            if (_state == STATE_NAME) {
                while (_index < _count) {
                    AvroFieldReader r = _fieldReaders[_index];
                    if (r.isSkipper()) {
                        ++_index;
                        r.skipValue(_parser);
                        continue;
                    }
                    String name = r.getName();
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
    }
}
