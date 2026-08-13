package tools.jackson.dataformat.avro.ser;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import tools.jackson.core.StreamWriteFeature;
import tools.jackson.dataformat.avro.AvroGenerator;

public final class ObjectWriteContext
    extends KeyValueContext
{
    protected final GenericRecord _record;

    /**
     * Cached field lookup map, built once at construction to avoid O(n)
     * linear scans on every {@link #writeName} call.
     */
    private final Map<String, Schema.Field> _fieldMap;

    /**
     * Definition of property that is to be written next, if any;
     * null if property is to be skipped.
     */
    protected Schema.Field _nextField;

    public ObjectWriteContext(AvroWriteContext parent, AvroGenerator generator,
            GenericRecord record, Object currValue)
    {
        super(parent, generator, record.getSchema(), currValue);
        _record = record;
        _fieldMap = _buildFieldMap(_schema);
    }

    private static Map<String, Schema.Field> _buildFieldMap(Schema schema) {
        // LinkedHashMap preserves field order for deterministic error messages
        Map<String, Schema.Field> map = new LinkedHashMap<>();
        for (Schema.Field f : schema.getFields()) {
            map.put(f.name(), f);
        }
        return map;
    }

    @Override
    public Object rawValue() { return _record; }


    @Override
    public final AvroWriteContext createChildArrayContext(Object currValue)
    {
        _verifyValueWrite();
        Schema.Field field = _findField();
        if (field == null) { // unknown, to ignore
            return new NopWriteContext(TYPE_ARRAY, this, _generator, currValue);
        }
        AvroWriteContext child = new ArrayWriteContext(this, _generator,
                _createArray(field.schema()), currValue);
        _record.put(_currentName, child.rawValue());
        return child;
    }

    @Override
    public AvroWriteContext createChildObjectContext(Object currValue) {
        _verifyValueWrite();
        Schema.Field field = _findField();
        if (field == null) { // unknown, to ignore
            return new NopWriteContext(TYPE_OBJECT, this, _generator, currValue);
        }
        AvroWriteContext child = _createObjectContext(field.schema(), currValue);
        _record.put(_currentName, child.rawValue());
        return child;
    }

    @Override
    public final boolean writeName(String name)
    {
        _currentName = name;
        _expectValue = true;
        Schema.Field field = _fieldMap.get(name);
        if (field == null) {
            _reportUnknownField(name);
            _nextField = null;
            return false;
        }
        _nextField = field;
        return true;
    }

    @Override
    public void writeValue(Object value)
    {
        _verifyValueWrite();
        if (_nextField != null) {
            // 26-Nov-2019, tatu: Should not be needed any more, handled at a later
            //    point in `NonBSGenericDatumWriter`
            /*
            Schema schema = _nextField.schema();
            if (schema.getType() == Schema.Type.FIXED) {
                if (value instanceof ByteBuffer) {
                    // 13-Nov-2014 josh: AvroGenerator wraps all binary values in ByteBuffers,
                    // but avro wants FIXED, so rewrap the array, copying if necessary
                    ByteBuffer bb = (ByteBuffer) value;
                    byte[] bytes = bb.array();
                    if (bb.arrayOffset() != 0 || bb.remaining() != bytes.length) {
                        bytes = Arrays.copyOfRange(bytes, bb.arrayOffset(), bb.remaining());
                    }
                    value = new GenericData.Fixed(schema, bytes);
                } else if (value instanceof byte[]) {
                    value = new GenericData.Fixed(schema, (byte[]) value);
                }
            }
            */
            _record.put(_nextField.pos(), value);
        }
    }

    @Override
    public void writeString(String value) {
        _verifyValueWrite();
        if (_nextField != null) {
            _record.put(_nextField.pos(), value);
        }
    }

    @Override
    public void writeNull() {
        _verifyValueWrite();
        if (_nextField != null) {
            _record.put(_nextField.pos(), null);
        }
    }

    protected final void _verifyValueWrite() {
        if (!_expectValue) {
            throw new IllegalStateException("Expecting FIELD_NAME, not value");
        }
        _expectValue = false;
    }

    protected Schema.Field _findField() {
        if (_currentName == null) {
            throw new IllegalStateException("No current field name");
        }
        Schema.Field f = _fieldMap.get(_currentName);
        if (f == null) {
            _reportUnknownField(_currentName);
        }
        return f;
    }

    protected void _reportUnknownField(String name) {
        if (!_generator.isEnabled(StreamWriteFeature.IGNORE_UNKNOWN)) {
            throw new IllegalStateException("No field named '"+_currentName+"'");
        }
    }
}
