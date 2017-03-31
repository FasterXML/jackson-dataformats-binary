package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonToken;

/**
 * Default-providing {@link AvroFieldReader} implementations for
 * structured types. Built solely based on JSON default values,
 * for now, so only two variants needed. In future may need to
 * tie to Avro type hierarchy more closely.
 */
public class StructDefaults
{
    public static AvroFieldReader createObjectDefaults(String name,
            List<AvroFieldReader> fieldReaders) {
        
        return AvroFieldReader.construct(name, new ObjectDefaults(
                null, null,
                fieldReaders.toArray(new AvroFieldReader[fieldReaders.size()])));
    }

    public static AvroFieldReader createArrayDefaults(String name,
            List<AvroFieldReader> fieldReaders) {
        
        return AvroFieldReader.construct(name, new ArrayDefaults(
                null, null,
                fieldReaders.toArray(new AvroFieldReader[fieldReaders.size()])));
    }

    protected static class ObjectDefaults extends MapReader
    {
        protected final AvroFieldReader[] _fieldReaders;

        public ObjectDefaults(AvroReadContext parent,
                AvroParserImpl parser, AvroFieldReader[] fieldReaders)
        {
            super(parent, parser, null, null, null);
            _fieldReaders = fieldReaders;
        }

        @Override
        public long getRemainingElements() {
            return _fieldReaders.length - _index;
        }

        @Override
        public MapReader newReader(AvroReadContext parent,
                AvroParserImpl parser) {
            return new ObjectDefaults(parent, parser, _fieldReaders);
        }

        @Override
        public JsonToken nextToken() throws IOException
        {
            switch (_state) {
            case STATE_START:
                _parser.setAvroContext(this);
                _state = STATE_NAME;
                return (_currToken = JsonToken.START_OBJECT);
            case STATE_NAME:
                if (_index < _fieldReaders.length) {
                    _state = STATE_VALUE;
                    _currentName = _fieldReaders[_index].getName();
                    return (_currToken = JsonToken.FIELD_NAME);
                }
                final AvroReadContext parent = getParent();
                _state = STATE_DONE;
                _parser.setAvroContext(parent);
                return (_currToken = JsonToken.END_OBJECT);
            case STATE_VALUE:
                _state = STATE_NAME;
                AvroFieldReader r = _fieldReaders[_index++];
                return (_currToken = r.readValue(this, _parser));
            default:
            }
            throwIllegalState(_state);
            return null;
        }

        @Override
        public void skipValue(AvroParserImpl parser) throws IOException {
            // never called defaults
        }
    }

    protected static class ArrayDefaults extends ArrayReader
    {
        protected final AvroFieldReader[] _valueReaders;

        public ArrayDefaults(AvroReadContext parent,
                AvroParserImpl parser, AvroFieldReader[] valueReaders)
        {
            super(parent, parser, null, null);
            _valueReaders = valueReaders;
        }

        @Override
        public ArrayReader newReader(AvroReadContext parent,
                AvroParserImpl parser) {
            return new ArrayDefaults(parent, parser, _valueReaders);
        }

        @Override
        public JsonToken nextToken() throws IOException
        {
            switch (_state) {
            case STATE_START:
                _parser.setAvroContext(this);
                _state = STATE_ELEMENTS;
                return (_currToken = JsonToken.START_ARRAY);
            case STATE_ELEMENTS:
                if (_index < _valueReaders.length) {
                    AvroFieldReader r = _valueReaders[_index++];
                    return (_currToken = r.readValue(this, _parser));
                }
                final AvroReadContext parent = getParent();
                _state = STATE_DONE;
                _parser.setAvroContext(parent);
                return (_currToken = JsonToken.END_ARRAY);
            default:
            }
            throwIllegalState(_state);
            return null;
        }

        @Override
        public void skipValue(AvroParserImpl parser) throws IOException {
            // never called defaults
        }
    }

}
