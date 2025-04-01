package tools.jackson.dataformat.avro;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import tools.jackson.databind.DatabindContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.jsontype.NamedType;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import tools.jackson.databind.jsontype.impl.ClassNameIdResolver;

/**
 * {@link tools.jackson.databind.jsontype.TypeIdResolver} for Avro type IDs embedded in schemas.
 * Avro generally uses class names, but we want to also support named subtypes so that developers
 * can easily remap the embedded type IDs to a different runtime class.
 */
public class AvroTypeIdResolver extends ClassNameIdResolver
{
    private static final long serialVersionUID = 3L;

    private final Map<String, Class<?>> _idTypes;

    public AvroTypeIdResolver(JavaType baseType,
            PolymorphicTypeValidator stv, Collection<NamedType> subTypes) {
        super(baseType, subTypes, stv);
        _idTypes = new HashMap<>();
        if (subTypes != null) {
            for (NamedType namedType : subTypes) {
                _idTypes.put(namedType.getName(), namedType.getType());
            }
        }
    }

    @Override
    protected JavaType _typeFromId(DatabindContext ctxt, String id)
    {
        // primitive types don't have subclasses
        if (_baseType.isPrimitive()) {
            return _baseType;
        }
        // check if there's a specific type we should be using for this ID
        Class<?> subType = _idTypes.get(id);
        if (subType != null) {
            id = _idFrom(ctxt, null, subType);
        }
        return super._typeFromId(ctxt, id);

        // 26-Nov-2019, tatu: Should not swallow exceptions; with 2.10+ we can get
        //    "Illegal subtype" accidentally and that should be propagated
/*
        try {
            return super._typeFromId(ctxt, id);
        } catch (InvalidTypeIdException | IllegalArgumentException e) {
            // AvroTypeDeserializer expects null if we can't map the type ID to a class; It will throw an appropriate error if we can't
            // find a usable type.
            return null;
        }
*/
    }
}
