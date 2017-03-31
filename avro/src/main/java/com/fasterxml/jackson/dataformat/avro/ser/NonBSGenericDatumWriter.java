package com.fasterxml.jackson.dataformat.avro.ser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;

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
        return AvroWriteContext.resolveUnionIndex(union, datum);
    }

    @Override
    protected void write(Schema schema, Object datum, Encoder out) throws IOException {
        Type t = schema.getType();
        if (t == Type.ENUM) {
            super.writeWithoutConversion(schema, GENERIC_DATA.createEnum(datum.toString(), schema), out);
            return;
        }
        if (datum instanceof String) {
            String str = (String) datum;
            final int len = str.length();
            if (t == Type.ARRAY && schema.getElementType().getType() == Type.INT) {
                ArrayList<Integer> chars = new ArrayList<>(len);
                for (int i = 0; i < len; ++i) {
                    chars.add((int) str.charAt(i));
                }
                super.writeWithoutConversion(schema, chars, out);
                return;
            }
            if (len == 1 && t == Type.INT) {
                super.writeWithoutConversion(schema, (int) str.charAt(0), out);
                return;
            }
        }
        // 09-Mar-2017, tatu: BigDecimal and BigInteger written using various
        //    possible representations so... 
        if (datum instanceof BigDecimal) {
            switch (t) {
            case STRING:
                super.writeWithoutConversion(schema, datum.toString(), out);
                return;
            case DOUBLE:
                super.writeWithoutConversion(schema, ((Number) datum).doubleValue(), out);
                return;
            default:
            }
        }
        if (datum instanceof BigInteger) {
            switch (t) {
            case STRING:
                super.writeWithoutConversion(schema, datum.toString(), out);
                return;
            case LONG:
                super.writeWithoutConversion(schema, ((Number) datum).longValue(), out);
                return;
            default:
            }
        }
        super.writeWithoutConversion(schema, datum, out);
//        super.write(schema, datum, out);
    }
}
