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

    private final static Class<?> CLS_STRING = String.class;
    private final static Class<?> CLS_BIG_DECIMAL = BigDecimal.class;
    private final static Class<?> CLS_BIG_INTEGER = BigInteger.class;
    
    public NonBSGenericDatumWriter(Schema root) {
	super(root);
    }

    @Override
    public int resolveUnion(Schema union, Object datum) {
        return AvroWriteContext.resolveUnionIndex(union, datum);
    }

    @Override
    protected void write(Schema schema, Object datum, Encoder out) throws IOException
    {
        if (datum == null) {
            super.writeWithoutConversion(schema, datum, out);
            return;
        }
        Type t = schema.getType();
        if (t == Type.ENUM) {
            super.writeWithoutConversion(schema, GENERIC_DATA.createEnum(datum.toString(), schema), out);
            return;
        }
        Class<?> raw = datum.getClass();
        if (raw == CLS_STRING) {
            if (t == Type.STRING) { // just short-circuit quickly it being the common case
                super.writeWithoutConversion(schema, datum, out);
                return;
            }
            if (t == Type.ARRAY && schema.getElementType().getType() == Type.INT) {
                String str = (String) datum;
                final int len = str.length();
                ArrayList<Integer> chars = new ArrayList<>(len);
                for (int i = 0; i < len; ++i) {
                    chars.add((int) str.charAt(i));
                }
                super.writeWithoutConversion(schema, chars, out);
                return;
            }
            if (t == Type.INT) {
                String str = (String) datum;
                final int len = str.length();
                if (len == 1) {
                    super.writeWithoutConversion(schema, (int) str.charAt(0), out);
                    return;
                }
            }
        }
        // 09-Mar-2017, tatu: BigDecimal and BigInteger written using various
        //    possible representations so... 
        else if (raw == CLS_BIG_DECIMAL) {
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
        else if (raw == CLS_BIG_INTEGER) {
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
        // EncodedDatum are already in an avro-encoded format and can be written out directly to the underlying encoder
        else if (datum instanceof EncodedDatum) {
            ((EncodedDatum) datum).write(out);
            return;
        }
        super.writeWithoutConversion(schema, datum, out);
//        super.write(schema, datum, out);
    }
}
