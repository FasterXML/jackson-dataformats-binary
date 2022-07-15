package tools.jackson.dataformat.avro.ser;

import org.apache.avro.Schema;

import tools.jackson.dataformat.avro.AvroGenerator;

/**
 * Shared base class for both Record- and Map-backed types.
 */
abstract class KeyValueContext extends AvroWriteContext
{
    protected String _currentName;
    
    protected boolean _expectValue = false;

    protected KeyValueContext(AvroWriteContext parent, AvroGenerator generator,
            Schema schema, Object currValue)
    {
        super(TYPE_OBJECT, parent, generator, schema, currValue);
    }

    @Override
    public final String currentName() { return _currentName; }

    @Override
    public boolean canClose() {
        return !_expectValue;
    }
    
    @Override
    public final void appendDesc(StringBuilder sb)
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
}
