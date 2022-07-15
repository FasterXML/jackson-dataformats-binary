package tools.jackson.dataformat.avro.ser;

import java.util.HashMap;
import java.util.Map;

import org.apache.avro.Schema;

import tools.jackson.dataformat.avro.AvroGenerator;

/**
 * Alternative to {@link ObjectWriteContext} that needs to be used with
 * Avro Map datatype.
 */
public final class MapWriteContext
    extends KeyValueContext
{
    protected final Map<String,Object> _data;

    public MapWriteContext(AvroWriteContext parent, AvroGenerator generator,
            Schema schema, Object currValue)
    {
        super(parent, generator, schema, currValue);
        _data = new HashMap<String,Object>();
    }

    @Override
    public Object rawValue() { return _data; }

    @Override
    public final boolean writeName(String name)
    {
        _currentName = name;
        _expectValue = true;
        return true;
    }

    @Override
    public final AvroWriteContext createChildArrayContext(Object currValue)
    {
        _verifyValueWrite();
        AvroWriteContext child = new ArrayWriteContext(this, _generator,
                _createArray(_schema.getValueType()), currValue);
        _data.put(_currentName, child.rawValue());
        return child;
    }

    @Override
    public final AvroWriteContext createChildObjectContext(Object currValue)
    {
        _verifyValueWrite();
        AvroWriteContext child = _createObjectContext(_schema.getValueType(), currValue);
        _data.put(_currentName, child.rawValue());
        return child;
    }

    @Override
    public void writeValue(Object value) {
        _verifyValueWrite();
        _data.put(_currentName, value);
    }

    @Override
    public void writeString(String value) {
        _verifyValueWrite();
        _data.put(_currentName, value);
    }

    @Override
    public void writeNull() {
        _verifyValueWrite();
        _data.put(_currentName, null);
    }

    protected final void _verifyValueWrite() {
        if (!_expectValue) {
            throw new IllegalStateException("Expecting FIELD_NAME, not value");
        }
        _expectValue = false;
    }
}
