package com.fasterxml.jackson.dataformat.avro.ser;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.reflect.Stringable;

import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaHelper;

/**
 * Need to sub-class to prevent encoder from crapping on writing an optional
 * Enum value (see [dataformat-avro#12])
 * 
 * @since 2.5
 */
public class NonBSGenericDatumWriter<D>
	extends GenericDatumWriter<D>
{
	public NonBSGenericDatumWriter(Schema root) {
		super(root);
	}

    @Override
    public int resolveUnion(Schema union, Object datum) {
        // Alas, we need a work-around first...
        if (datum == null) {
            return union.getIndexNamed(Type.NULL.getName());
        }
        List<Schema> schemas = union.getTypes();
        if (datum instanceof String) { // String or Enum or Character or char[]
            for (int i = 0, len = schemas.size(); i < len; i++) {
                Schema s = schemas.get(i);
                switch (s.getType()) {
                case STRING:
                case ENUM:
                    return i;
                case INT:
                    // Avro distinguishes between String and Character, whereas Jackson doesn't
                    // Check if the schema is expecting a Character and handle appropriately
                    if (Character.class.getName().equals(s.getProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS))) {
                        return i;
                    }
                    break;
                case ARRAY:
                    // Avro distinguishes between String and char[], whereas Jackson doesn't
                    // Check if the schema is expecting a char[] and handle appropriately
                    if (s.getElementType().getType() == Type.INT && Character.class
                    .getName().equals(s.getElementType().getProp(AvroSchemaHelper.AVRO_SCHEMA_PROP_CLASS))) {
                        return i;
                    }
                    break;
                default:
                }
            }
        } else if (datum instanceof BigDecimal) {
            int subOptimal = -1;
            for (int i = 0, len = schemas.size(); i < len; i++) {
                if (schemas.get(i).getType() == Type.STRING) {
                    return i;
                } else if (schemas.get(i).getType() == Type.DOUBLE) {
                    subOptimal = i;
                }
            }
            if (subOptimal > -1) {
                return subOptimal;
            }
        }

        // otherwise just default to base impl, stupid as it is...
        return super.resolveUnion(union, datum);
    }

    @Override
    protected void write(Schema schema, Object datum, Encoder out) throws IOException {
        // Cocerce numerical types, like BigDecimal -> double and BigInteger -> long
        if (datum instanceof Number) {
            switch (schema.getType()) {
            case LONG:
                super.write(schema, (((Number) datum).longValue()), out);
                return;
            case INT:
                super.write(schema, (((Number) datum).intValue()), out);
                return;
            case FLOAT:
                super.write(schema, (((Number) datum).floatValue()), out);
                return;
            case DOUBLE:
                super.write(schema, (((Number) datum).doubleValue()), out);
                return;
            case STRING:
                super.write(schema, datum.toString(), out);
                return;
            }
        }
        // Handle stringable classes
        if (schema.getType() == Type.STRING && datum != null && datum.getClass().getAnnotation(Stringable.class) != null) {
            super.write(schema, datum.toString(), out);
            return;
        }
        if (datum instanceof String) {
            String str = (String) datum;
            final int len = str.length();
            if (schema.getType() == Type.ARRAY && schema.getElementType().getType() == Type.INT) {
                ArrayList<Integer> chars = new ArrayList<>(len);
                for (int i = 0; i < len; ++i) {
                    chars.add((int) str.charAt(i));
                }
                super.write(schema, chars, out);
                return;
            }
            if (len == 1 && schema.getType() == Type.INT) {
                super.write(schema, (int) str.charAt(0), out);
                return;
            }
        }
        super.write(schema, datum, out);
    }
}
