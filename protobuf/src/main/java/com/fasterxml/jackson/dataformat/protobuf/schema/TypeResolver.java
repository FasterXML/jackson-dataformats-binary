package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.*;

import com.fasterxml.jackson.core.util.InternCache;
import com.squareup.protoparser.*;

/**
 * Stateful class needed to properly resolve type definitions of
 * protobuf message (and related types); some complexity coming
 * from possible nested nature of definitions.
 */
public class TypeResolver
{
    private final TypeResolver _parent;

    /**
     * For nested definitions we also need to know the name this context
     * has (not used for root): this is unqualified name (that is, only
     * name for context, not path)
     */
    private final String _contextName;

    /**
     * Mapping from types declared within this scope (root types for
     * root resolver; nested types for child resolvers)
     */
    private Map<String,MessageElement> _declaredMessageTypes;

    /**
     * Enum types visible at this level and deeper (nested enums with
     * scoped names), not including enums declared at outer levels.
     */
    private Map<String,ProtobufEnum> _enumTypes;

    /**
     * Mapping from names of types within this scope (with possible prefix as
     * necessary) into resolve types.
     */
    private Map<String,ProtobufMessage> _resolvedMessageTypes;

    protected TypeResolver(TypeResolver p, String name, Map<String,MessageElement> declaredMsgs,
            Map<String,ProtobufEnum> enums)
    {
        _parent = p;
        _contextName = name;
        _enumTypes = enums;
        if (declaredMsgs == null) {
            declaredMsgs = Collections.emptyMap();
        }
        _declaredMessageTypes = declaredMsgs;
        _resolvedMessageTypes = Collections.emptyMap();
    }

    /**
     * Main entry method for public API, for resolving specific root-level type and other
     * types it depends on.
     */
    public static ProtobufMessage resolve(Collection<TypeElement> nativeTypes, MessageElement rawType) {
        final TypeResolver rootR  = construct(null, null, nativeTypes);
        // Important: parent context for "root types", but child context for nested; further,
        // resolution happens in "child" context to allow proper referencing
        return TypeResolver.construct(rootR, rawType.name(), rawType.nestedElements())
                ._resolve(rawType);
    }

    protected ProtobufMessage resolve(TypeResolver parent, MessageElement rawType)
    {
        return TypeResolver.construct(this, rawType.name(), rawType.nestedElements())
                ._resolve(rawType);
    }

    protected static TypeResolver construct(TypeResolver parent, String localName,
            Collection<TypeElement> nativeTypes)
    {
        Map<String,MessageElement> declaredMsgs = null;
        Map<String,ProtobufEnum> declaredEnums = new LinkedHashMap<>();

        for (TypeElement nt : nativeTypes) {
            if (nt instanceof MessageElement) {
                if (declaredMsgs == null) {
                    declaredMsgs = new LinkedHashMap<String,MessageElement>();
                }
                declaredMsgs.put(nt.name(), (MessageElement) nt);
            } else if (nt instanceof EnumElement) {
                final ProtobufEnum enumType = constructEnum((EnumElement) nt);
                declaredEnums.put(nt.name(), enumType);
                // ... and don't forget parent scopes!
                if (parent != null) {
                    parent.addEnumType(_scopedName(localName, nt.name()), enumType);
                }
            } // no other known types?
        }
        return new TypeResolver(parent, localName, declaredMsgs, declaredEnums);
    }

    protected void addEnumType(String name, ProtobufEnum enumType) {
        _enumTypes.put(name, enumType);
        if (_parent != null) {
            _parent.addEnumType(_scopedName(name), enumType);
        }
    }

    protected static ProtobufEnum constructEnum(EnumElement nativeEnum)
    {
        final Map<String,Integer> valuesByName = new LinkedHashMap<String,Integer>();
        boolean standard = true;
        int exp = 0;

        for (EnumConstantElement v : nativeEnum.constants()) {
            int id = v.tag();
            if (standard && (id != exp)) {
                standard = false;
            }
            valuesByName.put(v.name(), id);
            ++exp;
        }
        // 17-Mar-2015, tatu: Number of intern()s here should be nominal;
        //    but intern()ing itself helps in keeping name/id enum translation fast
        String name = InternCache.instance.intern(nativeEnum.name());
        return new ProtobufEnum(name, valuesByName, standard);
    }

    protected ProtobufMessage _resolve(MessageElement rawType)
    {
        List<FieldElement> rawFields = rawType.fields();
        ProtobufField[] resolvedFields = new ProtobufField[rawFields.size()];

        ProtobufMessage message = new ProtobufMessage(rawType.name(), resolvedFields);
        // Important: add type itself as (being) resolved, to allow for self- and cyclic refs
        if (_parent != null) { // 09-Jul-2021, tatu: LGTM suggestion -- can it ever be null?!
            _parent.addResolvedMessageType(rawType.name(), message);
        }

        // and then resolve fields
        int ix = 0;
        for (FieldElement f : rawFields) {
            final DataType fieldType = f.type();
            // First: could it be we have a simple scalar type
            FieldType type = FieldTypes.findType(fieldType);
            ProtobufField pbf;

            if (type != null) { // simple type
                pbf = new ProtobufField(f, type);
            } else if (fieldType instanceof DataType.NamedType) {
                final String typeStr = ((DataType.NamedType) fieldType).name();

                // If not, a resolved local definition?
                ProtobufField resolvedF = _findLocalResolved(f, typeStr);
                if (resolvedF != null) {
                    pbf = resolvedF;
                } else {
                    // or, barring that local but as of yet unresolved message?
                    MessageElement nativeMt = _declaredMessageTypes.get(typeStr);
                    if (nativeMt != null) {
                        pbf = new ProtobufField(f, resolve(this, nativeMt));
                    } else {
                        // If not, perhaps parent might have an answer?
                        resolvedF = (_parent == null) ? null : _parent._findAnyResolved(f, typeStr);
                        if (resolvedF != null) {
                            pbf = resolvedF;
                        } else {
                            // Ok, we are out of options here...
                            StringBuilder enumStr = _knownEnums(new StringBuilder());
                            StringBuilder msgStr = _knownMsgs(new StringBuilder());
                            throw new IllegalArgumentException(String.format(
                                    "Unknown protobuf field type '%s' for field '%s' of MessageType '%s"
                                    +"' (known enum types: %s; known message types: %s)",
                                    typeStr, f.name(), rawType.name(), enumStr, msgStr));
                        }
                    }
                }
            } else {
                throw new IllegalArgumentException(String.format(
                        "Unrecognized DataType '%s' for field '%s'", fieldType.getClass().getName(), f.name()));
            }
            resolvedFields[ix++] = pbf;
        }
        ProtobufField first = (resolvedFields.length == 0) ? null : resolvedFields[0];

        // sort field array by index
        Arrays.sort(resolvedFields);

        // And then link the fields, to speed up iteration
        for (int i = 0, end = resolvedFields.length-1; i < end; ++i) {
            resolvedFields[i].assignNext(resolvedFields[i+1]);
        }
        message.init(first);
        return message;
    }

    protected void addResolvedMessageType(String name, ProtobufMessage toResolve) {
        if (_resolvedMessageTypes.isEmpty()) {
            _resolvedMessageTypes = new HashMap<String,ProtobufMessage>();
        }
        _resolvedMessageTypes.put(name, toResolve);
        // But also: for parent scopes
        if (_parent != null) {
            _parent.addResolvedMessageType(_scopedName(name), toResolve);
        }
    }

    private ProtobufField _findAnyResolved(FieldElement nativeField, String typeStr)
    {
        for (TypeResolver r = this; r != null; r = r._parent) {
            ProtobufField f = r._findLocalResolved(nativeField, typeStr);
            if (f != null) {
                return f;
            }
            f = r._findAndResolve(nativeField, typeStr);
            if (f != null) {
                return f;
            }
        }

        return null;
    }

    private ProtobufField _findAndResolve(FieldElement nativeField, String typeStr)
    {
        MessageElement nativeMt = _declaredMessageTypes.get(typeStr);
        if (nativeMt != null) {
            return new ProtobufField(nativeField, resolve(this, nativeMt));
        }
        return null;
    }

    private StringBuilder _knownEnums(StringBuilder sb) {
        if (_parent != null) {
            sb = _parent._knownEnums(sb);
        }
        for (String name : _enumTypes.keySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(name);
        }
        return sb;
    }

    private StringBuilder _knownMsgs(StringBuilder sb) {
        if (_parent != null) {
            sb = _parent._knownMsgs(sb);
        }
        for (String name : _declaredMessageTypes.keySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(name);
        }
        return sb;
    }

    private ProtobufField _findLocalResolved(FieldElement nativeField, String typeStr)
    {
        ProtobufMessage msg = _resolvedMessageTypes.get(typeStr);
        if (msg != null) {
            return new ProtobufField(nativeField, msg);
        }
        ProtobufEnum et = _enumTypes.get(typeStr);
        if (et != null) {
            return new ProtobufField(nativeField, et);
        }
        return null;
    }

    private final String _scopedName(String localName) {
        return _scopedName(_contextName, localName);
    }

    private final static String _scopedName(String contextName, String localName) {
        return new StringBuilder(contextName).append('.').append(localName).toString();
    }
}
