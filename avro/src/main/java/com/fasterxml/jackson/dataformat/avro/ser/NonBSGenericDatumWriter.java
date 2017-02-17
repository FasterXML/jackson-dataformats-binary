package com.fasterxml.jackson.dataformat.avro.ser;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.Encoder;

/**
 * Need to sub-class to prevent encoder from crapping on writing an optional
 * Enum value (see [dataformat-avro#12])
 * 
 * @since 2.5
 */
public class NonBSGenericDatumWriter<D>
	extends GenericDatumWriter<D>
{
	private static final GenericData GENERIC_DATA = GenericData.get();

	public NonBSGenericDatumWriter(Schema root) {
		super(root);
	}

	@Override
	public int resolveUnion(Schema union, Object datum) {
		// Alas, we need a work-around first...
		if (datum == null) {
			return union.getIndexNamed(Type.NULL.getName());
		}
		if (datum instanceof String) { // String or Enum
			List<Schema> schemas = union.getTypes();
			for (int i = 0, len = schemas.size(); i < len; ++i) {
				Schema s = schemas.get(i);
				switch (s.getType()) {
				case STRING:
				case ENUM:
					return i;
				case ARRAY:
					// Avro distinguishes between String and char[], whereas Jackson doesn't
					// Check if the schema is expecting a char[] and handle appropriately
					if (s.getElementType().getType() == Type.INT) {
						return i;
					}
				default:
				}
			}
		} else if( datum instanceof BigDecimal) {
			List<Schema> schemas = union.getTypes();
			for (int i = 0, len = schemas.size(); i < len; ++i) {
				Schema s = schemas.get(i);
				switch (s.getType()) {
				case DOUBLE:
					return i;
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
	    } else if (schema.getType() == Type.ENUM) {
			super.write(schema, GENERIC_DATA.createEnum(datum.toString(), schema), out);
		} else if (datum instanceof String && schema.getType() == Type.ARRAY && schema.getElementType().getType() == Type.INT) {
			Integer[] chars = new Integer[((String)datum).length()];
			char[] src = ((String)datum).toCharArray();
			for(int i = 0; i < chars.length; i++) {
				chars[i] = (int)src[i];
			}
			super.write(schema, Arrays.asList(chars), out);
	    } else {
	        super.write(schema, datum, out);
	    }
	}
}
