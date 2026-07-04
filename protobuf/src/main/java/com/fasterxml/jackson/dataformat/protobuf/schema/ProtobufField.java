package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.Collection;

import com.squareup.protoparser.FieldElement;
import com.squareup.protoparser.OptionElement;

import com.fasterxml.jackson.core.SerializableString;

public class ProtobufField
// sorted in increasing order
    implements Comparable<ProtobufField>
{
    /**
     * Numeric tag, unshifted
     */
    public final int id;

    /**
     * Combination of numeric tag and 3-bit wire type.
     */
    public final int typedTag;

    /**
     * Name of field in protoc definition
     */
    public final String name;

    public final FieldType type;

    /**
     * 3-bit id used on determining details of how values are serialized.
     */
    public final int wireType;

    public final boolean required, repeated, packed, deprecated;
    public final boolean usesZigZag;

    /**
     * For main type of {@link FieldType#MESSAGE}, reference to actual
     * message type definition.
     */
    protected ProtobufMessage messageType;

    /**
     * For fields of type {@link FieldType#ENUM}, mapping from names to ids.
     */
    protected final EnumLookup enumValues;

    /**
     * Link to next field within message definition; used for efficient traversal.
     * Due to inverse construction order need to be assigned after construction;
     * but functionally immutable.
     */
    public ProtobufField next;

    public final boolean isObject;

    public final boolean isStdEnum;

    public ProtobufField(FieldElement nativeField, FieldType type) {
        this(nativeField, type, false);
    }

    /**
     * @param isProto3 Whether enclosing schema uses proto3 syntax: affects default
     *    "packed" setting for repeated scalar fields when not explicitly specified
     *    (proto3 defaults to packed, proto2 to unpacked)
     */
    public ProtobufField(FieldElement nativeField, FieldType type, boolean isProto3) {
        this(nativeField, type, null, null, isProto3);
    }

    public ProtobufField(FieldElement nativeField, ProtobufMessage msg) {
        this(nativeField, FieldType.MESSAGE, msg, null, false);
    }

    public ProtobufField(FieldElement nativeField, ProtobufEnum et) {
        this(nativeField, FieldType.ENUM, null, et, false);
    }

    public ProtobufField(FieldElement nativeField, ProtobufEnum et, boolean isProto3) {
        this(nativeField, FieldType.ENUM, null, et, isProto3);
    }

    public static ProtobufField unknownField() {
        return new ProtobufField(null, FieldType.MESSAGE, null, null, false);
    }

    protected ProtobufField(FieldElement nativeField, FieldType type,
            ProtobufMessage msg, ProtobufEnum et, boolean isProto3)
    {
        this.type = type;
        wireType = type.getWireType();
        usesZigZag = type.usesZigZag();
        if (et == null) {
            enumValues = EnumLookup.empty();
            isStdEnum = false;
        } else {
            enumValues = EnumLookup.construct(et);
            isStdEnum = et.usesStandardIndexing();
        }
        messageType = msg;

        if (nativeField == null) { // for "unknown" field
            typedTag = id = 0;
            repeated = required = deprecated = packed = false;
            name = "UNKNOWN";
        } else {
            id = nativeField.tag();
            name = nativeField.name();
            switch (nativeField.label()) {
            case REPEATED:
                required = false;
                repeated = true;
                break;
            case REQUIRED:
                required = true;
                repeated = false;
                break;
            default:
                required = repeated = false;
                break;
            }
            /* 08-Apr-2015, tatu: Due to [https://github.com/square/protoparser/issues/90]
             *   we can't use 'isPacked()' in 3.1.5 (and probably deprecated has same issue);
             *   let's add a temporary workaround.
             */
            Boolean explicitPacked = _findBooleanOptionValue(nativeField, "packed");
            if (explicitPacked != null) {
                packed = explicitPacked.booleanValue();
            } else {
                // 01-Jul-2026: [dataformats-binary#134] proto3 defaults repeated
                //   scalar/enum fields to packed encoding unless overridden
                packed = repeated && isProto3 && type.isPackable();
            }
            deprecated = Boolean.TRUE.equals(_findBooleanOptionValue(nativeField, "deprecated"));

            // 13-Apr-2017, tatu: [databind#79] Need to write length-prefixed for packed arrays
            if (repeated && packed) {
                typedTag = (id << 3) + WireType.LENGTH_PREFIXED;
            } else {
                typedTag = (id << 3) + wireType;
            }

        }
        isObject = (type == FieldType.MESSAGE);
    }

    private static Boolean _findBooleanOptionValue(FieldElement f, String key)
    {
        for (OptionElement opt : f.options()) {
            if (key.equals(opt.name())) {
                Object val = opt.value();
                if (val instanceof Boolean) {
                    return (Boolean) val;
                }
                return Boolean.valueOf("true".equals(String.valueOf(val).trim()));
            }
        }
        return null;
    }

    public void assignMessageType(ProtobufMessage msgType) {
        if (type != FieldType.MESSAGE) {
            throw new IllegalStateException("Can not assign message type for non-message field '"+name+"'");
        }
        messageType = msgType;
    }

    public void assignNext(ProtobufField n) {
        if (this.next != null) {
            throw new IllegalStateException("Can not overwrite 'next' after being set");
        }
        this.next = n;
    }

    public final ProtobufMessage getMessageType() {
        return messageType;
    }

    public final ProtobufField nextOrThisIf(int idToMatch) {
        if ((next != null) && (next.id == idToMatch)) {
            return next;
        }
        // or maybe we actually have the id?
        if (idToMatch == id) {
            return this;
        }
        return null;
    }

    public final ProtobufField nextIf(String nameToMatch) {
        if (next != null) {
            if ((nameToMatch == next.name) || nameToMatch.equals(next.name)) {
                return next;
            }
        }
        return null;
    }

    public final int findEnumIndex(SerializableString key) {
        return enumValues.findEnumIndex(key);
    }

    public final int findEnumIndex(String key) {
        return enumValues.findEnumIndex(key);
    }
    public final String findEnumByIndex(int index) {
        return enumValues.findEnumByIndex(index);
    }

    public Collection<String> getEnumValues() {
        return enumValues.getEnumValues();
    }

    public final boolean isArray() {
        return repeated;
    }

    public final boolean isValidFor(int typeTag) {
        return (typeTag == wireType)
                // 13-Apr-2017, tatu: to fix [dataformats-binary#76]
                // 03-Jul-2026, tatu: [dataformats-binary#134] A repeated scalar/enum
                //   field may arrive packed (LENGTH_PREFIXED) regardless of the schema's
                //   declared `packed` flag -- proto3 permits either encoding on the wire,
                //   so tolerance must key off the type, not the schema default.
                || (repeated && type.isPackable() && typeTag == WireType.LENGTH_PREFIXED);
    }

    /**
     * Accessor for deciding whether an incoming, repeated field should be read
     * using "packed" (single length-prefixed block) encoding.
     *<p>
     * For genuinely packable types (scalar numeric/enum/boolean) the native
     * unpacked wire type differs from {@code LENGTH_PREFIXED}, so the actual wire
     * type is unambiguous and authoritative: proto3 permits either encoding on the
     * wire regardless of the schema's declared {@code packed} flag.
     *<p>
     * For non-packable types (String/Bytes/Message) a single element and a
     * jackson-style "packed" block are <b>both</b> {@code LENGTH_PREFIXED}, so the
     * wire type cannot distinguish them; there we must fall back to the schema's
     * declared {@code packed} flag.
     *
     * @since 2.21.5 [dataformats-binary#134]
     */
    public final boolean isPackedInWire(int typeTag) {
        if (type.isPackable()) {
            return repeated && (typeTag == WireType.LENGTH_PREFIXED);
        }
        return packed;
    }

    @Override
    public String toString() // for debugging
    {
        return "Field '"+name+"', tag="+typedTag+", wireType="+wireType+", fieldType="+type;
    }

    @Override
    public int compareTo(ProtobufField other) {
        return Integer.compare(id, other.id);
    }
}
