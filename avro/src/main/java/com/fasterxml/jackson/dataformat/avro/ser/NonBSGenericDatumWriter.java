package com.fasterxml.jackson.dataformat.avro.ser;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
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
	    } else {
	        super.write(schema, datum, out);
	    }
	}
}
