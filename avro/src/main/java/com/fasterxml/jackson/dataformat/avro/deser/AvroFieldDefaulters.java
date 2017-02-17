package com.fasterxml.jackson.dataformat.avro.deser;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.codehaus.jackson.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Factory class for various default providers
 */
public class AvroFieldDefaulters
{
    public static AvroFieldReader createDefaulter(String name,
            JsonNode defaultAsNode, Schema schema) {
        switch (defaultAsNode.asToken()) {
        case VALUE_TRUE:
            return new ScalarDefaults.BooleanDefaults(name, true);
        case VALUE_FALSE:
            return new ScalarDefaults.BooleanDefaults(name, false);
        case VALUE_NULL:
            return new ScalarDefaults.NullDefaults(name);
        case VALUE_NUMBER_FLOAT:
            switch (defaultAsNode.getNumberType()) {
            case FLOAT:
                return new ScalarDefaults.FloatDefaults(name, (float) defaultAsNode.asDouble());
            case DOUBLE:
            case BIG_DECIMAL: // TODO: maybe support separately?
            default:
                return new ScalarDefaults.DoubleDefaults(name, defaultAsNode.asDouble());
            }
        case VALUE_NUMBER_INT:
            switch (defaultAsNode.getNumberType()) {
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
                Iterator<Map.Entry<String,JsonNode>> it = defaultAsNode.getFields();
                List<AvroFieldReader> readers = new ArrayList<AvroFieldReader>();
                while (it.hasNext()) {
                    Map.Entry<String,JsonNode> entry = it.next();
                    String propName = entry.getKey();
                    readers.add(createDefaulter(
                        propName,
                        entry.getValue(),
                        schema.getType() == Type.MAP ? schema.getValueType() : schema.getField(propName).schema()
                    ));
                }
                return StructDefaults.createObjectDefaults(name, readers, schema);
            }
        case START_ARRAY:
        {
            List<AvroFieldReader> readers = new ArrayList<AvroFieldReader>();
            for (JsonNode value : defaultAsNode) {
                readers.add(createDefaulter("", value, schema.getElementType()));
            }
            return StructDefaults.createArrayDefaults(name, readers, schema);
        }
        default:
        }
        return null;
    }
}
