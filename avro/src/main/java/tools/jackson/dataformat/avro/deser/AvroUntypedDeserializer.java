package tools.jackson.dataformat.avro.deser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.KeyDeserializer;
import tools.jackson.databind.deser.jdk.UntypedObjectDeserializer;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.jsontype.TypeIdResolver;
import tools.jackson.databind.type.TypeFactory;

/**
 * Deserializer for handling when we have no target type, just schema type. Normally, the {@link UntypedObjectDeserializer} doesn't look
 * for native type information when handling scalar values, but Avro sometimes includes type information in the schema for scalar values;
 * This subclass checks for the presence of valid type information and calls out to the type deserializer even for scalar values. The
 * same goes for map keys.
 */
public class AvroUntypedDeserializer
    extends UntypedObjectDeserializer
{
    protected TypeDeserializer _typeDeserializer;

    public AvroUntypedDeserializer(JavaType listType, JavaType mapType)
    {
        super(listType, mapType);
    }

    public static AvroUntypedDeserializer construct(TypeFactory f) {
        return new AvroUntypedDeserializer(f.constructType(List.class),
                f.constructType(Map.class));
    }

    @Override
    public void resolve(DeserializationContext ctxt)
    {
        JavaType obType = ctxt.constructType(Object.class);
        // 26-Sep-2017, tatu: I think this is wrong, but has been that way for a while
        //    so won't change quite yet
        _typeDeserializer = ctxt.findTypeDeserializer(obType);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException
    {
        // Make sure we have a native type ID *AND* that we can resolve it to a type; otherwise, we'll end up in a recursive loop
        if (p.canReadTypeId()) {
             Object typeId = p.getTypeId();
             if (typeId != null) {
                if (_typeDeserializer != null) {
                    TypeIdResolver resolver = _typeDeserializer.getTypeIdResolver();
                    if (resolver != null && resolver.typeFromId(ctxt, typeId.toString()) != null) {
                        return _typeDeserializer.deserializeTypedFromAny(p, ctxt);
                    }
                }
             }
        }
        return super.deserialize(p, ctxt);
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt,
            TypeDeserializer typeDeserializer) throws JacksonException {
        // Use type deserializer if we have type information, even for scalar values
        if (typeDeserializer != null) {
            if (p.canReadTypeId()) {
                Object typeId = p.getTypeId();
                if (typeId != null) {
                    TypeIdResolver resolver = typeDeserializer.getTypeIdResolver();
                    // Make sure that we actually can resolve the type ID, otherwise we'll end up in a recursive loop
                    if (resolver != null && resolver.typeFromId(ctxt, p.getTypeId().toString()) != null) {
                        return typeDeserializer.deserializeTypedFromAny(p, ctxt);
                    }
                }
            }
        }
        return super.deserializeWithType(p, ctxt, typeDeserializer);
    }

    /**
     * Method called to map a JSON Object into a Java value.
     */
    // Would we just be better off deferring to the Map<Object,Object> deserializer?
    @Override
    protected Object mapObject(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        Object key1;
        JsonToken t = p.currentToken();

        if (t == JsonToken.START_OBJECT) {

            key1 = p.nextName();
        } else if (t == JsonToken.PROPERTY_NAME) {
            key1 = p.currentName();
        } else {
            if (t != JsonToken.END_OBJECT) {
                return ctxt.handleUnexpectedToken(getValueType(ctxt), p);
            }
            key1 = null;
        }
        if (key1 == null) {
            // empty map might work; but caller may want to modify... so better just give small modifiable
            return new LinkedHashMap<>(2);
        }

        KeyDeserializer deserializer = null;
        if (p.getTypeId() != null) {
            if (_typeDeserializer != null) {
                TypeIdResolver idResolver = _typeDeserializer.getTypeIdResolver();
                JavaType keyType = idResolver.typeFromId(ctxt, p.getTypeId().toString());
                if (keyType != null) {
                    deserializer = ctxt.findKeyDeserializer(keyType, null);
                }
            }
        }
        if (deserializer != null) {
            key1 = deserializer.deserializeKey(key1.toString(), ctxt);
        }
        p.nextToken();
        Object value1 = deserialize(p, ctxt);

        Object key2 = p.nextName();
        if (key2 == null) { // has to be END_OBJECT, then
            // single entry; but we want modifiable
            LinkedHashMap<Object, Object> result = new LinkedHashMap<>(2);
            result.put(key1, value1);
            return result;
        }
        if (deserializer != null) {
            key2 = deserializer.deserializeKey(key2.toString(), ctxt);
        }

        p.nextToken();
        Object value2 = deserialize(p, ctxt);

        Object key = p.nextName();

        if (key == null) {
            LinkedHashMap<Object, Object> result = new LinkedHashMap<>(4);
            result.put(key1, value1);
            result.put(key2, value2);
            return result;
        }
        // And then the general case; default map size is 16
        LinkedHashMap<Object, Object> result = new LinkedHashMap<>();
        result.put(key1, value1);
        result.put(key2, value2);

        do {
            if (deserializer != null) {
                key = deserializer.deserializeKey(key.toString(), ctxt);
            }
            p.nextToken();
            result.put(key, deserialize(p, ctxt));
        } while ((key = p.nextName()) != null);
        return result;
    }
}
