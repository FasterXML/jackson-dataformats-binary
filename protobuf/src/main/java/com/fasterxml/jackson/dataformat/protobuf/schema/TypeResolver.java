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

    private Map<String,MessageElement> _nativeMessageTypes;

    private Map<String,ProtobufEnum> _enumTypes;

    private Map<String,ProtobufMessage> _resolvedMessageTypes;
    
    protected TypeResolver(TypeResolver p, Map<String,MessageElement> nativeMsgs,
            Map<String,ProtobufEnum> enums)
    {
        _parent = p;
        if (enums == null) {
            enums = Collections.emptyMap();
        }
        _enumTypes = enums;
        if (nativeMsgs == null) {
            nativeMsgs = Collections.emptyMap();
        }
        _nativeMessageTypes = nativeMsgs;
        _resolvedMessageTypes = Collections.emptyMap();
    }

    public static TypeResolver construct(Collection<TypeElement> nativeTypes) {
        return construct(null, nativeTypes);
    }
    
    protected static TypeResolver construct(TypeResolver parent, Collection<TypeElement> nativeTypes)
    {
        Map<String,MessageElement> nativeMessages = null;
        Map<String,ProtobufEnum> enumTypes = null;
        
        for (TypeElement nt : nativeTypes) {
            if (nt instanceof MessageElement) {
                if (nativeMessages == null) {
                    nativeMessages = new LinkedHashMap<String,MessageElement>();
                }
                nativeMessages.put(nt.name(), (MessageElement) nt);
            } else if (nt instanceof EnumElement) {
                if (enumTypes == null) {
                    enumTypes = new LinkedHashMap<String,ProtobufEnum>();
                }
                enumTypes.put(nt.name(), _constructEnum((EnumElement) nt));
            } // no other known types?
        }
        return new TypeResolver(parent, nativeMessages, enumTypes);
    }

    protected static ProtobufEnum _constructEnum(EnumElement nativeEnum)
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

    public ProtobufMessage resolve(MessageElement rawType)
    {
        ProtobufMessage msg = _findResolvedMessage(rawType.name());
        if (msg != null) {
            return msg;
        }
        /* Since MessageTypes can contain other type definitions, it is
         * important that we actually create a new context, that is,
         * new TypeResolver instance, and call resolution on that.
         */
        return TypeResolver.construct(this, rawType.nestedElements())
                ._resolve(rawType);
    }
        
    protected ProtobufMessage _resolve(MessageElement rawType)
    {
        List<FieldElement> rawFields = rawType.fields();
        ProtobufField[] resolvedFields = new ProtobufField[rawFields.size()];
        
        ProtobufMessage message = new ProtobufMessage(rawType.name(), resolvedFields);
        // Important: add type itself as (being) resolved, to allow for self-refs:
        if (_resolvedMessageTypes.isEmpty()) {
            _resolvedMessageTypes = new HashMap<String,ProtobufMessage>();
        }
        _resolvedMessageTypes.put(rawType.name(), message);

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
                    MessageElement nativeMt = _nativeMessageTypes.get(typeStr);
                    if (nativeMt != null) {
                        pbf = new ProtobufField(f,
                                TypeResolver.construct(this, nativeMt.nestedElements())._resolve(nativeMt));
                    } else {
                        // If not, perhaps parent might have an answer?
                        resolvedF = _parent._findAnyResolved(f, typeStr);
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

    private ProtobufMessage _findResolvedMessage(String typeStr)
    {
        ProtobufMessage msg = _resolvedMessageTypes.get(typeStr);
        if ((msg == null) && (_parent !=null)) {
            return _parent._findResolvedMessage(typeStr);
        }
        return msg;
    }

    private ProtobufField _findAnyResolved(FieldElement nativeField, String typeStr)
    {
        ProtobufField f = _findLocalResolved(nativeField, typeStr);
        if (f == null) {
            MessageElement nativeMt = _nativeMessageTypes.get(typeStr);
            if (nativeMt != null) {
                return new ProtobufField(nativeField,
                        TypeResolver.construct(this, nativeMt.nestedElements())._resolve(nativeMt));
            }
            if (_parent != null) {
                return _parent._findAnyResolved(nativeField, typeStr);
            }
        }
        return f;
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
        for (String name : _nativeMessageTypes.keySet()) {
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
}
