package com.fasterxml.jackson.dataformat.avro.ser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.avro.Conversion;
import org.apache.avro.Conversions;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.apache.avro.data.TimeConversions;
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
    private final static Class<?> CLS_STRING = String.class;
    private final static Class<?> CLS_BIG_DECIMAL = BigDecimal.class;
    private final static Class<?> CLS_BIG_INTEGER = BigInteger.class;

    private final GenericData genericData;

    public NonBSGenericDatumWriter(Schema root) {
	    super(root);
	    genericData = GenericData.get();

        Map<String, Conversion<?>> conversions = new HashMap<>();
	    if(Type.RECORD == root.getType()) {
	        for(Schema.Field field:root.getFields()) {
	            Schema fieldSchema;

	            if(Type.UNION == field.schema().getType()) {
	                fieldSchema = AvroWriteContext.resolveUnionType(field.schema(), null);
                } else {
	                fieldSchema = field.schema();
                }
	            if(null==fieldSchema.getLogicalType()) {
	                continue;
                }
                String logicalTypeName = fieldSchema.getLogicalType().getName();
	            if(conversions.containsKey(logicalTypeName)){
	                continue;
                }
                switch (logicalTypeName) {
                    case "decimal":
                        conversions.put(logicalTypeName, new Conversions.DecimalConversion());
                        break;
                    case "date":
                        conversions.put(logicalTypeName, new TimeConversions.DateConversion());
                        break;
                    case "time-millis":
                        conversions.put(logicalTypeName, new TimeConversions.TimeConversion());
                        break;
                    case "time-micros":
                        conversions.put(logicalTypeName, new TimeConversions.TimeMicrosConversion());
                        break;
                    case "timestamp-millis":
                        conversions.put(logicalTypeName, new TimeConversions.TimestampConversion());
                        break;
                    case "timestamp-micros":
                        conversions.put(logicalTypeName, new TimeConversions.TimestampMicrosConversion());
                        break;
                        default:
                            throw new UnsupportedOperationException(
                                String.format("%s is not a supported logical type.", logicalTypeName)
                            );
                }
            }
            for(Conversion<?> conversion: conversions.values()) {
	            genericData.addLogicalTypeConversion(conversion);
            }
        }
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
        switch (schema.getType()) {
        case STRING: // just short-circuit quickly it being the common case
            Class<?> raw = datum.getClass();
            if (raw == CLS_STRING) {
                writeString(datum, out);
                return;
            }
            if ((raw == CLS_BIG_DECIMAL) || (raw == CLS_BIG_INTEGER)) {
                writeString(datum.toString(), out);
                return;
            }
            break;
        case ENUM:
            super.writeWithoutConversion(schema, genericData.createEnum(datum.toString(), schema), out);
            return;
        case INT:
            if (datum.getClass() == CLS_STRING) {
                String str = (String) datum;
                final int len = str.length();
                if (len == 1) {
                    super.writeWithoutConversion(schema, (int) str.charAt(0), out);
                    return;
                }
            }
            break;
        case LONG:
            if (datum.getClass() == CLS_BIG_INTEGER) {
                datum = ((BigInteger) datum).longValue();
            }
            break;
        case DOUBLE:
            if (datum.getClass() == CLS_BIG_DECIMAL) {
                datum = ((BigDecimal) datum).doubleValue();
            }
            break;
        case ARRAY:
            if (datum.getClass() == CLS_STRING) {
                if (schema.getElementType().getType() == Type.INT) {
                    String str = (String) datum;
                    final int len = str.length();
                    ArrayList<Integer> chars = new ArrayList<>(len);
                    for (int i = 0; i < len; ++i) {
                        chars.add((int) str.charAt(i));
                    }
                    super.writeWithoutConversion(schema, chars, out);
                    return;
                }
            }
            break;
        default:
        }
        // EncodedDatum are already in an avro-encoded format and can be written out directly to the underlying encoder
        if (datum instanceof EncodedDatum) {
            ((EncodedDatum) datum).write(out);
            return;
        }
//        super.writeWithoutConversion(schema, datum, out);
        super.write(schema, datum, out);
    }
}
