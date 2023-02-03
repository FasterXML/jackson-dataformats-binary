package tools.jackson.dataformat.avro.deser;

import java.io.IOException;

import tools.jackson.core.JsonToken;
import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.dataformat.avro.schema.AvroSchemaHelper;

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

    protected RecordReader(AvroReadContext parent, AvroFieldReader[] fieldReaders, AvroParserImpl parser, String typeId)
    {
        super(parent, TYPE_OBJECT, typeId);
        _fieldReaders = fieldReaders;
        _parser = parser;
        _count = fieldReaders.length;
    }

    @Override
    public abstract RecordReader newReader(AvroReadContext parent, AvroParserImpl parser);

    @Override
    public String currentName() { return _currentName; }

    @Override
    public boolean consumesNoContent() {
        // 26-Aug-2019, tatu: As per [dataformats-binary#177], 0-field Records consume
        //   no content. It may be possible other variants exist too (fields with "Constant"
        //   value?), but let's start with the simple case
        return _fieldReaders.length == 0;
    }

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

    protected final int _matchAtEndObject() throws IOException
    {
        _state = STATE_DONE;
        _parser.setAvroContext(getParent());
        return PropertyNameMatcher.MATCH_END_OBJECT;
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
        if (_currToken == JsonToken.END_OBJECT || _currToken == JsonToken.START_OBJECT) {
            return super.getTypeId();
        }
        if (_currToken == JsonToken.PROPERTY_NAME) {
            return AvroSchemaHelper.getTypeId(String.class);
        }
        // When reading a value type ID, the index pointer has already advanced to the next field, so look at the previous
        return _fieldReaders[_index - 1].getTypeId();
    }

    /*
    /**********************************************************************
    /* Implementations
    /**********************************************************************
     */

    public final static class Std
        extends RecordReader
    {
        public Std(AvroFieldReader[] fieldReaders, String typeId) {
            super(null, fieldReaders, null, typeId);
        }

        public Std(AvroReadContext parent, AvroFieldReader[] fieldReaders, AvroParserImpl parser, String typeId) {
            super(parent, fieldReaders, parser, typeId);
        }

        @Override
        public RecordReader newReader(AvroReadContext parent, AvroParserImpl parser) {
            return new Std(parent, _fieldReaders, parser, _typeId);
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
                        JsonToken t = JsonToken.PROPERTY_NAME;
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
        public String nextName() throws IOException
        {
            if (_state == STATE_NAME) {
                if (_index < _count) {
                    String name = _fieldReaders[_index].getName();
                    _currentName = name;
                    _state = STATE_VALUE;
                    _currToken = JsonToken.PROPERTY_NAME;
                    return name;
                }
                _nextAtEndObject();
            } else if (_state == STATE_END) {
                _nextAtEndObject();
            } else {
                nextToken();
            }
            return null;
        }

        @Override
        public int nextNameMatch(PropertyNameMatcher matcher) throws IOException {
            if (_state == STATE_NAME) {
                if (_index < _count) {
                    String name = _fieldReaders[_index].getName();
                    _currentName = name;
                    _state = STATE_VALUE;
                    _currToken = JsonToken.PROPERTY_NAME;
                    return matcher.matchName(name);
                }
                return _matchAtEndObject();
            }
            if (_state == STATE_END) {
                return _matchAtEndObject();
            }
            // 26-Aug-2019, tatu: Is this correct thing to do?
            nextToken();
            return PropertyNameMatcher.MATCH_ODD_TOKEN;
        }
    }

    public final static class Resolving
        extends RecordReader
    {
        public Resolving(AvroFieldReader[] fieldReaders, String typeId) {
            super(null, fieldReaders, null, typeId);
        }
        public Resolving(AvroReadContext parent, AvroFieldReader[] fieldReaders, AvroParserImpl parser, String typeId) {
            super(parent, fieldReaders, parser, typeId);
        }

        @Override
        public RecordReader newReader(AvroReadContext parent, AvroParserImpl parser) {
            return new Resolving(parent, _fieldReaders, parser, _typeId);
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
                    return (_currToken = JsonToken.PROPERTY_NAME);
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
        public String nextName() throws IOException
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
                    _currToken = JsonToken.PROPERTY_NAME;
                    return name;
                }
                _nextAtEndObject();
            } else {
                nextToken();
            }
            return null;
        }

        @Override
        public int nextNameMatch(PropertyNameMatcher matcher) throws IOException {
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
                    _currToken = JsonToken.PROPERTY_NAME;
                    return matcher.matchName(name);
                }
                return _matchAtEndObject();
            }
            nextToken();
            return PropertyNameMatcher.MATCH_ODD_TOKEN;
        }
    }
}
