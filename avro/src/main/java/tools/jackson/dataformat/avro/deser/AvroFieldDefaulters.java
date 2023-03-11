package tools.jackson.dataformat.avro.deser;

import java.util.*;

import tools.jackson.databind.JsonNode;

/**
 * Factory class for various default providers
 */
public class AvroFieldDefaulters
{
    public static AvroFieldReader createDefaulter(String name,
            JsonNode defaultAsNode)
    {
        switch (defaultAsNode.asToken()) {
        case VALUE_TRUE:
            return new ScalarDefaults.BooleanDefaults(name, true);
        case VALUE_FALSE:
            return new ScalarDefaults.BooleanDefaults(name, false);
        case VALUE_NULL:
            return new ScalarDefaults.NullDefaults(name);
        case VALUE_NUMBER_FLOAT:
            switch (defaultAsNode.numberType()) {
            case FLOAT:
                return new ScalarDefaults.FloatDefaults(name, (float) defaultAsNode.asDouble());
            case DOUBLE:
            case BIG_DECIMAL: // TODO: maybe support separately?
            default:
                return new ScalarDefaults.DoubleDefaults(name, defaultAsNode.asDouble());
            }
        case VALUE_NUMBER_INT:
            switch (defaultAsNode.numberType()) {
            case INT:
                return new ScalarDefaults.FloatDefaults(name, defaultAsNode.asInt());
            case BIG_INTEGER: // TODO: maybe support separately?
            case LONG:
            default:
                return new ScalarDefaults.FloatDefaults(name, defaultAsNode.asLong());
            }
        case VALUE_STRING:
            return new ScalarDefaults.StringDefaults(name, defaultAsNode.asText());
        case START_OBJECT:
            {
                Iterator<Map.Entry<String,JsonNode>> it = defaultAsNode.fields();
                List<AvroFieldReader> readers = new ArrayList<AvroFieldReader>();
                while (it.hasNext()) {
                    Map.Entry<String,JsonNode> entry = it.next();
                    String propName = entry.getKey();
                    readers.add(createDefaulter(propName, entry.getValue()));
                }
                return StructDefaults.createObjectDefaults(name, readers);
            }
        case START_ARRAY:
        {
            List<AvroFieldReader> readers = new ArrayList<AvroFieldReader>();
            for (JsonNode value : defaultAsNode) {
                readers.add(createDefaulter("", value));
            }
            return StructDefaults.createArrayDefaults(name, readers);
        }
        default:
        }
        return null;
    }

    // 23-Jul-2019, tatu: With Avro 1.9, likely changed  to use "raw" JDK containers?
    //   Code would look more like this:
/*
    public static AvroFieldReader createDefaulter(String name,
            Object defaultValue)
    {

        if (defaultValue == null) {
            return new ScalarDefaults.NullDefaults(name);
        }
        if (defaultValue instanceof Boolean) {
            return new ScalarDefaults.BooleanDefaults(name, ((Boolean) defaultValue).booleanValue());
        }
        if (defaultValue instanceof String) {
            return new ScalarDefaults.StringDefaults(name, (String) defaultValue);
        }
        if (defaultValue instanceof Number) {
            Number n = (Number) defaultValue;
            if (defaultValue instanceof Long) {
                return new ScalarDefaults.LongDefaults(name, n.longValue());
            }
            if (defaultValue instanceof Integer) {
                return new ScalarDefaults.IntDefaults(name, n.intValue());
            }
            if ((defaultValue instanceof Double) || (defaultValue instanceof java.math.BigDecimal)) {
                return new ScalarDefaults.DoubleDefaults(name, n.doubleValue());
            }
            if (defaultValue instanceof Float) {
                return new ScalarDefaults.FloatDefaults(name, n.floatValue());
            }
        }
        if (defaultValue instanceof List<?>) {
            List<AvroFieldReader> readers = new ArrayList<AvroFieldReader>();
            for (Object value : ((List<?>) defaultValue)) {
                readers.add(createDefaulter("", value));
            }
            return StructDefaults.createArrayDefaults(name, readers);
        }
        if (defaultValue instanceof Map<?,?>) {
            List<AvroFieldReader> readers = new ArrayList<AvroFieldReader>();
            @SuppressWarnings("unchecked")
            Map<String, Object> mapValue = (Map<String, Object>) defaultValue;
            for (Map.Entry<String, Object> entry : mapValue.entrySet()) {
                readers.add(createDefaulter(entry.getKey(), entry.getValue()));
            }
            return StructDefaults.createObjectDefaults(name, readers);
        }

        throw new IllegalArgumentException("Unrecognized default value type: "
                + ClassUtil.classNameOf(defaultValue));
    }
*/
}
