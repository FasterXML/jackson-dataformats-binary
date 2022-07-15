package tools.jackson.dataformat.avro.ser;

import org.apache.avro.generic.GenericArray;

import tools.jackson.dataformat.avro.AvroGenerator;

public class ArrayWriteContext
    extends AvroWriteContext
{
    protected final GenericArray<Object> _array;
    
    public ArrayWriteContext(AvroWriteContext parent, AvroGenerator generator,
            GenericArray<Object> array, Object currValue)
    {
        super(TYPE_ARRAY, parent, generator, array.getSchema(), currValue);
        _array = array;
    }

    @Override
    public Object rawValue() { return _array; }

    @Override
    public final AvroWriteContext createChildArrayContext(Object currValue) {
        GenericArray<Object> arr = _createArray(_schema.getElementType());
        _array.add(arr);
        return new ArrayWriteContext(this, _generator, arr, currValue);
    }

    @Override
    public AvroWriteContext createChildObjectContext(Object currValue) {
        AvroWriteContext child = _createObjectContext(_schema.getElementType(), currValue);
        _array.add(child.rawValue());
        return child;
    }

    @Override
    public void writeValue(Object value) {
        _array.add(value);
    }

    @Override
    public void writeString(String value) {
        _array.add(value);
    }

    @Override
    public void writeNull() {
        _array.add(null);
    }

    @Override
    public void appendDesc(StringBuilder sb)
    {
        sb.append('[');
        sb.append(getCurrentIndex());
        sb.append(']');
    }
}