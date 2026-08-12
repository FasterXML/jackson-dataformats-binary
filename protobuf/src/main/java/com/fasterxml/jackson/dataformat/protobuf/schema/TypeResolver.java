package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.*;

import com.squareup.protoparser.*;

import com.fasterxml.jackson.core.util.InternCache;

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

    /**
     * Whether enclosing schema (single .proto file) uses proto3 syntax: affects
     * default "packed" setting for repeated scalar/enum fields.
     *
     * @since 2.21.5 [dataformats-binary#134]
     */
    private final boolean _isProto3;

    protected TypeResolver(TypeResolver p, String name,
            Map<String,MessageElement> declaredMsgs,
            Map<String,ProtobufEnum> enums, boolean isProto3)
    {
        _parent = p;
        _contextName = name;
        _enumTypes = enums;
        _isProto3 = isProto3;
        if (declaredMsgs == null) {
            declaredMsgs = Collections.emptyMap();
        }
        _declaredMessageTypes = declaredMsgs;
        _resolvedMessageTypes = Collections.emptyMap();
    }

    /**
     * Main entry method for public API, for resolving specific root-level type and other
     * types it depends on.
     *
     * @deprecated Since 2.22
     */
    @Deprecated
    public static ProtobufMessage resolve(Collection<TypeElement> nativeTypes, MessageElement rawType) {
        return resolve(nativeTypes, rawType, false);
    }

    /**
     * @since 2.21.5 [dataformats-binary#134]
     */
    public static ProtobufMessage resolve(Collection<TypeElement> nativeTypes, MessageElement rawType,
            boolean isProto3) {
        final TypeResolver rootR  = construct(null, null, nativeTypes, isProto3);
        // Important: parent context for "root types", but child context for nested; further,
        // resolution happens in "child" context to allow proper referencing
        return TypeResolver.construct(rootR, rawType.name(), rawType.nestedElements(), isProto3)
                ._resolve(rawType);
    }

    protected ProtobufMessage resolve(TypeResolver parent, MessageElement rawType)
    {
        return TypeResolver.construct(this, rawType.name(), rawType.nestedElements(), _isProto3)
                ._resolve(rawType);
    }

    protected static TypeResolver construct(TypeResolver parent, String localName,
            Collection<TypeElement> nativeTypes, boolean isProto3)
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
        return new TypeResolver(parent, localName, declaredMsgs, declaredEnums, isProto3);
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
        List<OneOfElement> oneOfs = rawType.oneOfs();
        // 01-Jul-2026, tatu: [dataformats-binary#134] Fields declared inside a
        //   `oneof` block live in a separate list from regular fields and were
        //   silently dropped during resolution; merge them in so they're not lost.
        if (!oneOfs.isEmpty()) {
            List<FieldElement> merged = new ArrayList<FieldElement>(rawFields);
            for (OneOfElement oneOf : oneOfs) {
                merged.addAll(oneOf.fields());
            }
            rawFields = merged;
        }
        ProtobufField[] resolvedFields = new ProtobufField[rawFields.size()];

        ProtobufMessage message = new ProtobufMessage(rawType.name(), resolvedFields);
        // 15-Jul-2026, tatu: [dataformats-binary#712] protoc descriptor sets desugar
        //   `map<K,V>` into a nested entry message flagged `map_entry`; note that here
        //   so a `repeated` field of this type can be re-exposed as a map (below).
        if (_hasMapEntryOption(rawType)) {
            message.markAsMapEntry();
        }
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
                pbf = new ProtobufField(f, type, _isProto3);
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
            } else if (fieldType instanceof DataType.MapType) {
                // 15-Jul-2026, tatu: [dataformats-binary#712] `map<K,V>` is encoded
                //   exactly like a `repeated` entry sub-message; synthesize that entry
                //   type and expose the field as a map.
                pbf = _resolveMapField(f, (DataType.MapType) fieldType, rawType);
            } else {
                throw new IllegalArgumentException(String.format(
                        "Unrecognized DataType '%s' for field '%s'", fieldType.getClass().getName(), f.name()));
            }
            // [dataformats-binary#712] A `repeated <Name>Entry` field whose entry message
            //   is `map_entry`-flagged (from a protoc descriptor set) is really a map;
            //   re-expose it idiomatically, matching the `.proto` `map<K,V>` path.
            if (pbf.repeated && !pbf.isMap && (pbf.type == FieldType.MESSAGE)) {
                final ProtobufMessage entryMsg = pbf.getMessageType();
                if ((entryMsg != null) && entryMsg.isMapEntry()) {
                    pbf = _constructMapField(f, entryMsg, rawType);
                }
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

    /**
     * Resolves a {@code map<K,V>} field by synthesizing the "entry" sub-message
     * ({@code key} = tag 1, {@code value} = tag 2) that protobuf uses to encode maps
     * on the wire, then wrapping it in a repeated, map-flagged {@link ProtobufField}.
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    private ProtobufField _resolveMapField(FieldElement f, DataType.MapType mapType,
            MessageElement rawType)
    {
        final DataType keyType = mapType.keyType();
        final DataType valueType = mapType.valueType();
        _verifyMapKeyType(keyType, f, rawType);

        // Build the synthetic entry message: `message XxxEntry { key = 1; value = 2; }`.
        // Both are proto3 "singular" (OPTIONAL label) fields; resolving through the
        // normal machinery reuses all scalar / enum / message / nested type handling,
        // and resolves the value type against this (the enclosing) scope.
        final String entryName = _mapEntryName(f.name());
        final MessageElement entryElem = MessageElement.builder()
                .name(entryName)
                .addField(FieldElement.builder()
                        .name("key").tag(1)
                        .label(FieldElement.Label.OPTIONAL)
                        .type(keyType)
                        .build())
                .addField(FieldElement.builder()
                        .name("value").tag(2)
                        .label(FieldElement.Label.OPTIONAL)
                        .type(valueType)
                        .build())
                .build();
        final ProtobufMessage entryMsg = resolve(this, entryElem);
        return _constructMapField(f, entryMsg, rawType);
    }

    /**
     * Builds the map-flagged {@link ProtobufField} for given entry message, verifying
     * first that the entry actually carries the {@code key} (tag 1) / {@code value}
     * (tag 2) pair that map decoding requires, and that the key is of a type protobuf
     * permits.
     *<p>
     * Both are guaranteed by construction on the {@code .proto} path, but not on the
     * descriptor-set path, where the entry message comes from the input: a
     * {@code map_entry}-flagged message with a missing or ill-typed key/value is not
     * something {@code protoc} emits, but a hand-built or corrupt descriptor can carry
     * one, and it must fail as a schema error rather than as a {@code NullPointerException}
     * from the codec later on.
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    private static ProtobufField _constructMapField(FieldElement f, ProtobufMessage entryMsg,
            MessageElement rawType)
    {
        final ProtobufField keyField = entryMsg.field(1);
        final ProtobufField valueField = entryMsg.field(2);
        if ((keyField == null) || (valueField == null)) {
            throw new IllegalArgumentException(String.format(
                    "Invalid `map` entry type '%s' for field '%s' in MessageType '%s': entry"
                    + " message must declare both 'key' (tag 1) and 'value' (tag 2), but %s missing",
                    entryMsg.getName(), f.name(), rawType.name(),
                    (keyField == null)
                        ? ((valueField == null) ? "both are" : "'key' is") : "'value' is"));
        }
        if (!_isValidMapKeyType(keyField.type)) {
            throw new IllegalArgumentException(String.format(
                    "Illegal key type (%s) for `map` field '%s' in MessageType '%s': protobuf"
                    + " map keys must be an integral type, `bool` or `string`",
                    keyField.type, f.name(), rawType.name()));
        }
        return new ProtobufField(f, entryMsg, keyField, valueField);
    }

    /**
     * @return Whether given (resolved) type is one protobuf permits as a {@code map} key.
     *    Mirrors {@link #_verifyMapKeyType}, which checks the same restriction against the
     *    as-declared type on the {@code .proto} path; this one works on the resolved type,
     *    so it covers the descriptor-set path too.
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    private static boolean _isValidMapKeyType(FieldType t)
    {
        switch (t) {
        case STRING:
        case BOOLEAN:
        case VINT32_STD:
        case VINT32_Z:
        case FIXINT32:
        case VINT64_STD:
        case VINT64_Z:
        case FIXINT64:
            return true;
        default: // DOUBLE, FLOAT, BYTES, ENUM, MESSAGE
            return false;
        }
    }

    /**
     * @return Whether given message declaration carries the {@code map_entry} option
     *    (set by {@code protoc} on the synthetic entry type of a {@code map} field).
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    private static boolean _hasMapEntryOption(MessageElement rawType)
    {
        for (OptionElement opt : rawType.options()) {
            if ("map_entry".equals(opt.name())) {
                Object v = opt.value();
                if (v instanceof Boolean) {
                    return ((Boolean) v).booleanValue();
                }
                return "true".equals(String.valueOf(v).trim());
            }
        }
        return false;
    }

    /**
     * Verifies that the key type of a {@code map<K,V>} field is one protobuf permits:
     * any integral type, {@code bool} or {@code string} (not floating-point, {@code bytes},
     * enum or message).
     */
    private static void _verifyMapKeyType(DataType keyType, FieldElement f, MessageElement rawType)
    {
        if (keyType instanceof DataType.ScalarType) {
            switch ((DataType.ScalarType) keyType) {
            case DOUBLE:
            case FLOAT:
            case BYTES:
            case ANY:
                break; // not allowed as key -- fall through to throw
            default:
                return; // integral types, bool and string are all valid keys
            }
        }
        throw new IllegalArgumentException(String.format(
                "Illegal key type (%s) for `map` field '%s' in MessageType '%s': protobuf"
                + " map keys must be an integral type, `bool` or `string`",
                keyType, f.name(), rawType.name()));
    }

    /**
     * Derives the name of the synthetic entry message for a {@code map} field.
     *<p>
     * The name deliberately contains characters that can not occur in a protobuf
     * identifier ({@code [A-Za-z_][A-Za-z0-9_]*}), so that it can never collide with a
     * type the schema itself declares. Resolving the entry publishes it into the
     * enclosing scope (see {@link #_resolve}), so a {@code protoc}-style
     * {@code <FieldName>Entry} name would shadow a same-named declared type for any
     * field resolved after the map field -- silently, and depending on declaration
     * order. The name is only ever used for diagnostics and internal lookup, never for
     * resolving references written in the schema.
     *
     * @since 2.21.6 [dataformats-binary#712]
     */
    private static String _mapEntryName(String fieldName)
    {
        if (fieldName == null || fieldName.isEmpty()) {
            return "map<?>";
        }
        return "map<" + fieldName + ">";
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
        // [dataformats-binary#73] Handle dot-notation references to nested message types
        // (e.g. "OuterType.InnerType")
        return _findDottedType(nativeField, typeStr);
    }

    /**
     * Try to resolve a dot-notation type reference (e.g. {@code "OuterType.InnerType"})
     * by navigating the message type hierarchy declared at this scope level.
     */
    private ProtobufField _findDottedType(FieldElement nativeField, String typeStr)
    {
        int dotIx = typeStr.indexOf('.');
        if (dotIx <= 0) {
            return null;
        }
        String outerName = typeStr.substring(0, dotIx);
        String innerPath = typeStr.substring(dotIx + 1);
        MessageElement outerMsg = _declaredMessageTypes.get(outerName);
        if (outerMsg == null) {
            return null;
        }
        // Create a resolver in the context of the outer type and recursively
        // resolve the remaining path (handles arbitrary nesting depth)
        TypeResolver outerResolver = TypeResolver.construct(this, outerName, outerMsg.nestedElements(), _isProto3);
        return outerResolver._findAndResolve(nativeField, innerPath);
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
            return new ProtobufField(nativeField, et, _isProto3);
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
