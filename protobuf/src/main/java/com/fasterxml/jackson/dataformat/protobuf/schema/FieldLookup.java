package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.*;

/**
 * Helper class used for cases where {@link ProtobufField} instances
 * need to be looked up by name. Basically a more specialized Map
 * implementation.
 */
public abstract class FieldLookup
{
    public static FieldLookup empty() {
        return Empty.instance;
    }

    public static FieldLookup construct(ProtobufField[] fields)
    {
        if (fields.length == 0) { // can this occur?
            return Empty.instance;
        }
        switch (fields.length) {
        case 1:
            return new Small1(fields[0]);
        case 2:
            return new Small2(fields[0], fields[1]);
        case 3:
            return new Small3(fields[0], fields[1], fields[2]);
        }
        // General-purpose "big" one needed:
        return Big.construct(fields);
    }

    public abstract ProtobufField findField(String key);

    static class Empty extends FieldLookup {
        public final static Empty instance = new Empty();

        private Empty() {
            super();
        }

        @Override
        public ProtobufField findField(String key) {
            return null;
        }
    }

    static class Small1 extends FieldLookup
    {
        protected final String key1;
        protected final ProtobufField field1;

        private Small1(ProtobufField f) {
            key1 = f.name;
            field1 = f;
        }

        @Override
        public ProtobufField findField(String key) {
            if (key == key1 || key.equals(key1)) {
                return field1;
            }
            return null;
        }
    }

    final static class Small2 extends FieldLookup
    {
        protected final String key1, key2;
        protected final ProtobufField field1, field2;

        private Small2(ProtobufField f1, ProtobufField f2) {
            key1 = f1.name;
            field1 = f1;
            key2 = f2.name;
            field2 = f2;
        }

        @Override
        public ProtobufField findField(String key) {
            if (key == key1) {
                return field1;
            }
            if (key == key2) {
                return field2;
            }
            if (key.equals(key1)) {
                return field1;
            }
            if (key.equals(key2)) {
                return field2;
            }
            return null;
        }
    }

    final static class Small3 extends FieldLookup
    {
        protected final String key1, key2, key3;
        protected final ProtobufField field1, field2, field3;

        private Small3(ProtobufField f1, ProtobufField f2, ProtobufField f3) {
            key1 = f1.name;
            field1 = f1;
            key2 = f2.name;
            field2 = f2;
            key3 = f3.name;
            field3 = f3;
        }

        @Override
        public ProtobufField findField(String key) {
            if (key == key1) {
                return field1;
            }
            if (key == key2) {
                return field2;
            }
            if (key == key3) {
                return field3;
            }
            // usually should get interned key, but if not, try equals:
            return _find2(key);
        }

        private final ProtobufField _find2(String key) {
            if (key.equals(key1)) {
                return field1;
            }
            if (key.equals(key2)) {
                return field2;
            }
            if (key.equals(key3)) {
                return field3;
            }
            return null;
        }
    }
    
    /**
     * Raw mapping from keys to indices, optimized for fast access via
     * better memory efficiency. Hash area divide in three; main hash,
     * half-size secondary, followed by as-big-as-needed spillover.
     */
    final static class Big extends FieldLookup
    {
        private final int _hashMask, _spillCount;

        private final String[] _keys;
        private final ProtobufField[] _fields;

        private Big(int hashMask, int spillCount, String[] keys, ProtobufField[] fields)
        {
            _hashMask = hashMask;
            _spillCount = spillCount;
            _keys = keys;
            _fields = fields;
        }

        public static Big construct(ProtobufField[] allFields)
        {
            // First: calculate size of primary hash area
            final int size = findSize(allFields.length);
            final int mask = size-1;
            // and allocate enough to contain primary/secondary, expand for spillovers as need be
            int alloc = size + (size>>1);
            String[] keys = new String[alloc];
            ProtobufField[] fieldHash = new ProtobufField[alloc];
            int spills = 0;

            for (ProtobufField field : allFields) {
                String key = field.name;

                int slot = key.hashCode() & mask;

                // primary slot not free?
                if (keys[slot] != null) {
                    // secondary?
                    slot = size + (slot >> 1);
                    if (keys[slot] != null) {
                        // ok, spill over.
                        slot = size + (size >> 1) + spills;
                        ++spills;
                        if (slot >= keys.length) {
                            keys = Arrays.copyOf(keys, keys.length + 4);
                            fieldHash = Arrays.copyOf(fieldHash, fieldHash.length + 4);
                        }
                    }
                }
                keys[slot] = key;
                fieldHash[slot] = field;
            }
            return new Big(mask, spills, keys, fieldHash);
        }

        @Override
        public ProtobufField findField(String key) {
            int slot = key.hashCode() & _hashMask;
            String match = _keys[slot];
            if ((match == key) || key.equals(match)) {
                return _fields[slot];
            }
            if (match == null) {
                return null;
            }
            // no? secondary?
            slot = (_hashMask+1) + (slot>>1);
            match = _keys[slot];
            if ((match == key) || key.equals(match)) {
                return _fields[slot];
            }
            // or spill?
            return _findFromSpill(key);
        }

        private final static int findSize(int size)
        {
            if (size <= 5) {
                return 8;
            }
            if (size <= 12) {
                return 16;
            }
            int needed = size + (size >> 2); // at most 80% full
            int result = 32;
            while (result < needed) {
                result += result;
            }
            return result;
        }

        private ProtobufField _findFromSpill(String key) {
            int hashSize = _hashMask+1;
            int i = hashSize + (hashSize>>1);
            for (int end = i + _spillCount; i < end; ++i) {
                if (key.equals(_keys[i])) {
                    return _fields[i];
                }
            }
            return null;
        }
    }
}
