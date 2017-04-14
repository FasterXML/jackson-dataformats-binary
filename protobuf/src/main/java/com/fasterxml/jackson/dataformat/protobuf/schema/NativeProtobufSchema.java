package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.*;

import com.squareup.protoparser.*;

/**
 * Helper class used for wrapping a "raw" protobuf schema (as read by
 * "protoparser" library); and used
 * as input for creating specific {@link ProtobufSchema} to use for
 * reading/writing protobuf encoded content
 */
public class NativeProtobufSchema
{
    protected final String _name;
    protected final Collection<TypeElement> _nativeTypes;

    protected volatile String[] _messageNames;

    protected NativeProtobufSchema(ProtoFile input)
    {
        this(input.filePath(), input.typeElements());
    }
    
    protected NativeProtobufSchema(String name, Collection<TypeElement> types)
    {
        _name = name;
        _nativeTypes = types;
    }
    
    public static NativeProtobufSchema construct(ProtoFile input) {
        return new NativeProtobufSchema(input);
    }
    
    public static NativeProtobufSchema construct(String name, Collection<TypeElement> types) {
        return new NativeProtobufSchema(name, types);
    }
    
    /**
     * Method for checking whether specified message type is defined by
     * the native schema
     */
    public boolean hasMessageType(String messageTypeName)
    {
        for (TypeElement type : _nativeTypes) {
            if (messageTypeName.equals(type.name())) {
                if (type instanceof MessageElement) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Factory method for constructing Jackson-digestible schema using specified Message type
     * from native protobuf schema.
     */
    public ProtobufSchema forType(String messageTypeName)
    {
        MessageElement msg = _messageType(messageTypeName);
        if (msg == null) {
            throw new IllegalArgumentException("Protobuf schema definition (name '"+_name
                    +"') has no message type with name '"+messageTypeName+"': known types: "
                    +getMessageNames());
        }
        return new ProtobufSchema(this, TypeResolver.construct(_nativeTypes).resolve(msg));
    }

    /**
     * Factory method for constructing Jackson-digestible schema using the first
     * Message type defined in the underlying native protobuf schema.
     */
    public ProtobufSchema forFirstType()
    {
        MessageElement msg = _firstMessageType();
        if (msg == null) {
            throw new IllegalArgumentException("Protobuf schema definition (name '"+_name
                    +"') contains no message type definitions");
        }
        return new ProtobufSchema(this, TypeResolver.construct(_nativeTypes).resolve(msg));
    }

    public List<String> getMessageNames() {
        if (_messageNames == null) {
            _messageNames = _getMessageNames();
        }
        return Arrays.asList(_messageNames);
    }
    
    @Override
    public String toString() {
        return toString(_name);
    }
    
    public String toString(String name) {
        ProtoFile.Builder builder = ProtoFile.builder(name);
        builder.addTypes(_nativeTypes);
        return builder.build().toSchema();
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    
    protected MessageElement _firstMessageType() {
        for (TypeElement type : _nativeTypes) {
            if (type instanceof MessageElement) {
                return (MessageElement) type;
            }
        }
        return null;
    }

    protected MessageElement _messageType(String name) {
        for (TypeElement type : _nativeTypes) {
            if ((type instanceof MessageElement)
                    && name.equals(type.name())) {
                return (MessageElement) type;
            }
        }
        return null;
    }

    private String[] _getMessageNames() {
        ArrayList<String> names = new ArrayList<String>();
        for (TypeElement type : _nativeTypes) {
            if (type instanceof MessageElement) {
                names.add(type.name());
            }
        }
        return names.toArray(new String[names.size()]);
    }
}
