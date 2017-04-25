package com.fasterxml.jackson.dataformat.avro.deser;

import java.io.IOException;
import java.util.LinkedHashMap;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.deser.ResolvableDeserializer;
import com.fasterxml.jackson.databind.deser.std.UntypedObjectDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;

/**
 * Deserializer for handling when we have no target type, just schema type. Normally, the {@link UntypedObjectDeserializer} doesn't look
 * for native type information when handling scalar values, but Avro sometimes includes type information in the schema for scalar values;
 * This subclass checks for the presence of valid type information and calls out to the type deserializer even for scalar values. The
 * same goes for map keys.
 *
 * @since 2.9
 */
public class AvroUntypedDeserializer
    extends UntypedObjectDeserializer
    implements ResolvableDeserializer
{
    private static final long serialVersionUID = 1L;

    protected JavaType _typeObject;
    protected TypeDeserializer _typeDeserializer;

    public AvroUntypedDeserializer() { super(); }

    @Override
    public void resolve(DeserializationContext ctxt)
        throws JsonMappingException
    {
        _typeObject = ctxt.constructType(Object.class);
        _typeDeserializer = ctxt.getConfig().findTypeDeserializer(_typeObject);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
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
            TypeDeserializer typeDeserializer) throws IOException {
        // Use type deserializer if we have type information, even for scalar values
        if (p.canReadTypeId() && p.getTypeId() != null && typeDeserializer != null) {
            TypeIdResolver resolver = typeDeserializer.getTypeIdResolver();
            // Make sure that we actually can resolve the type ID, otherwise we'll end up in a recursive loop
            if (resolver != null && resolver.typeFromId(ctxt, p.getTypeId().toString()) != null) {
                return typeDeserializer.deserializeTypedFromAny(p, ctxt);
            }
        }
        return super.deserializeWithType(p, ctxt, typeDeserializer);
    }

    /**
     * Method called to map a JSON Object into a Java value.
     */
    // Would we just be better off deferring to the Map<Object,Object> deserializer?
    @Override
    protected Object mapObject(JsonParser p, DeserializationContext ctxt) throws IOException {
        Object key1;
        JsonToken t = p.getCurrentToken();

        if (t == JsonToken.START_OBJECT) {

            key1 = p.nextFieldName();
        } else if (t == JsonToken.FIELD_NAME) {
            key1 = p.getCurrentName();
        } else {
            if (t != JsonToken.END_OBJECT) {
                return ctxt.handleUnexpectedToken(handledType(), p);
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

        Object key2 = p.nextFieldName();
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

        Object key = p.nextFieldName();

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
        } while ((key = p.nextFieldName()) != null);
        return result;
    }
}
