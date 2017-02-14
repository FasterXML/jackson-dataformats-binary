package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;

import org.apache.avro.io.BinaryDecoder;

import com.fasterxml.jackson.core.JsonToken;

public class StructDefaults
{
    public static AvroFieldReader createObjectDefaults(String name,
            String[] fieldNames, AvroStructureReader[] fieldReaders) {
        return AvroFieldReader.construct(name, new ObjectDefaults(
                null, null, null,
                fieldNames, fieldReaders));
    }

    protected static class ObjectDefaults extends MapReader
    {
        protected final String[] _fieldNames;
        protected final AvroStructureReader[] _fieldReaders;

        public ObjectDefaults(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder decoder,
                String[] fieldNames, AvroStructureReader[] fieldReaders)
        {
            super(parent, parser, decoder);
            _fieldNames = fieldNames;
            _fieldReaders = fieldReaders;
        }

        @Override
        public MapReader newReader(AvroReadContext parent,
                AvroParserImpl parser, BinaryDecoder decoder) {
            return new ObjectDefaults(parent, parser, decoder,
                    _fieldNames, _fieldReaders);
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
                if (_index < _fieldNames.length) {
                    _state = STATE_VALUE;
                    _currentName = _fieldNames[_index];
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
                AvroStructureReader r = _fieldReaders[_index].newReader(this, _parser, _decoder);
                _parser.setAvroContext(r);
                ++_index;
                return (_currToken = r.nextToken());
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
