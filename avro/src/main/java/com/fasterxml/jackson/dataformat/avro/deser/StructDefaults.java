package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.util.List;

import org.apache.avro.io.BinaryDecoder;

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
                null, null, null,
                fieldReaders.toArray(new AvroFieldReader[fieldReaders.size()])));
    }

    public static AvroFieldReader createArrayDefaults(String name,
            List<AvroFieldReader> fieldReaders) {
        
        return AvroFieldReader.construct(name, new ArrayDefaults(
                null, null, null,
                fieldReaders.toArray(new AvroFieldReader[fieldReaders.size()])));
    }

    protected static class ObjectDefaults extends MapReader
    {
        protected final AvroFieldReader[] _fieldReaders;

        public ObjectDefaults(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder decoder,
                AvroFieldReader[] fieldReaders)
        {
            super(parent, parser, decoder);
            _fieldReaders = fieldReaders;
        }

        @Override
        public MapReader newReader(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder decoder) {
            return new ObjectDefaults(parent, parser, decoder, _fieldReaders);
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
                // as per [dataformats-binary#38], may need to reset, instead of bailing out
                if (parent.inRoot()) {
                    if (!DecodeUtil.isEnd(_decoder)) {
                        _index = 0;
                        _state = STATE_START;
                        return (_currToken = JsonToken.END_OBJECT);
                    }
                }
                _state = STATE_DONE;
                _parser.setAvroContext(parent);
                return (_currToken = JsonToken.END_OBJECT);
            case STATE_VALUE:
                _state = STATE_NAME;
                AvroFieldReader r = _fieldReaders[_index++];
                return (_currToken = r.readValue(this, _parser, _decoder));
            default:
            }
            throwIllegalState(_state);
            return null;
        }

        @Override
        public void skipValue(BinaryDecoder decoder) throws IOException {
            // never called defaults
        }
    }

    protected static class ArrayDefaults extends ArrayReader
    {
        protected final AvroFieldReader[] _valueReaders;

        public ArrayDefaults(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder decoder,
                AvroFieldReader[] valueReaders)
        {
            super(parent, parser, decoder);
            _valueReaders = valueReaders;
        }

        @Override
        public ArrayReader newReader(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder decoder) {
            return new ArrayDefaults(parent, parser, decoder, _valueReaders);
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
                    return (_currToken = r.readValue(this, _parser, _decoder));
                }
                final AvroReadContext parent = getParent();
                // as per [dataformats-binary#38], may need to reset, instead of bailing out
                if (parent.inRoot()) {
                    if (!DecodeUtil.isEnd(_decoder)) {
                        _index = 0;
                        _state = STATE_START;
                        return (_currToken = JsonToken.END_ARRAY);
                    }
                }
                _state = STATE_DONE;
                _parser.setAvroContext(parent);
                return (_currToken = JsonToken.END_ARRAY);
            default:
            }
            throwIllegalState(_state);
            return null;
        }

        @Override
        public void skipValue(BinaryDecoder decoder) throws IOException {
            // never called defaults
        }
    }

}
