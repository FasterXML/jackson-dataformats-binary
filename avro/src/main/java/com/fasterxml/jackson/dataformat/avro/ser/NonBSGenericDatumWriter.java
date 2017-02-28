package com.fasterxml.jackson.dataformat.avro.ser;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.Encoder;

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
		for (int i = 0, len = schemas.size(); i < len; i++) {
			Schema s = schemas.get(i);
			if (datum instanceof BigDecimal && s.getType() == Type.DOUBLE) {
				return i;
			}
			if (datum instanceof String) { // String or Enum or Character or char[]
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
		}
		// otherwise just default to base impl, stupid as it is...
		return super.resolveUnion(union, datum);
	}

	@Override
	protected void write(Schema schema, Object datum, Encoder out) throws IOException {
	    if ((schema.getType() == Type.DOUBLE) && datum instanceof BigDecimal) {
	        out.writeDouble(((BigDecimal)datum).doubleValue());
		} else if (datum instanceof String && schema.getType() == Type.ARRAY && schema.getElementType().getType() == Type.INT) {
			ArrayList<Integer> chars = new ArrayList<>(((String) datum).length());
			char[]             src   = ((String) datum).toCharArray();
			for (int i = 0; i < src.length; i++) {
				chars.add((int) src[i]);
			}
			super.write(schema, chars, out);
		} else if (datum instanceof String && ((String) datum).length() == 1 && schema.getType() == Type.INT) {
			super.write(schema, (int) ((String) datum).charAt(0), out);
		} else {
	        super.write(schema, datum, out);
	    }
	}
}
