package com.fasterxml.jackson.dataformat.protobuf.schema;

import java.util.*;

import com.fasterxml.jackson.core.SerializableString;

/**
 * Helper class used for doing efficient lookups of protoc enums
 * given enum name caller provides. Ideally this would be avoided,
 * but at this point translation is unfortunately necessary.
 */
public abstract class EnumLookup
{
    public static EnumLookup empty() {
        return Empty.instance;
    }

    public static EnumLookup construct(ProtobufEnum enumDef)
    {
        Map<String,Integer> enumEntries = enumDef.valueMapping();
        if (enumEntries.isEmpty()) { // can this occur?
            return Empty.instance;
        }
        List<Map.Entry<String,Integer>> l = new ArrayList<Map.Entry<String,Integer>>();
        for (Map.Entry<String,Integer> entry : enumDef.valueMapping().entrySet()) {
            l.add(entry);
        }
        switch (l.size()) {
        case 1:
            return new Small1(l.get(0).getKey(), l.get(0).getValue());
        case 2:
            return new Small2(l.get(0).getKey(), l.get(0).getValue(),
                    l.get(1).getKey(), l.get(1).getValue());
        case 3:
            return new Small3(l.get(0).getKey(), l.get(0).getValue(),
                    l.get(1).getKey(), l.get(1).getValue(),
                    l.get(2).getKey(), l.get(2).getValue());
        }

        // General-purpose "big" one needed:
        return Big.construct(l);
    }

    public abstract String findEnumByIndex(int index);

    public abstract int findEnumIndex(SerializableString key);

    public abstract int findEnumIndex(String key);

    public abstract Collection<String> getEnumValues();

    static class Empty extends EnumLookup {
        public final static Empty instance = new Empty();

        private Empty() {
            super();
        }

        @Override
        public int findEnumIndex(SerializableString key) {
            return -1;
        }

        @Override
        public int findEnumIndex(String key) {
            return -1;
        }

        @Override
        public Collection<String> getEnumValues() {
            return Collections.emptySet();
        }

        @Override
        public String findEnumByIndex(int index) {
            return null;
        }
    }

    static class Small1 extends EnumLookup
    {
        protected final String key1;
        protected final int index1;

        private Small1(String key, int index) {
            key1 = key;
            index1 = index;
        }

        @Override
        public String findEnumByIndex(int index) {
            if (index == index1) {
                return key1;
            }
            return null;
        }

        @Override
        public int findEnumIndex(SerializableString key) {
            if (key1.equals(key.getValue())) {
                return index1;
            }
            return -1;
        }

        @Override
        public int findEnumIndex(String key) {
            if (key1.equals(key)) {
                return index1;
            }
            return -1;
        }

        @Override
        public Collection<String> getEnumValues() {
            return Collections.singletonList(key1);
        }
    }

    final static class Small2 extends EnumLookup
    {
        protected final String key1, key2;
        protected final int index1, index2;

        private Small2(String k1, int i1, String k2, int i2) {
            key1 = k1;
            index1 = i1;
            key2 = k2;
            index2 = i2;
        }

        @Override
        public String findEnumByIndex(int index) {
            if (index == index1) {
                return key1;
            }
            if (index == index2) {
                return key2;
            }
            return null;
        }

        @Override
        public int findEnumIndex(SerializableString key0) {
            String key = key0.getValue();
            // should be canonical so check equals first
            if (key1 == key) {
                return index1;
            }
            if (key2 == key) {
                return index2;
            }
            if (key1.equals(key)) {
                return index1;
            }
            if (key2.equals(key)) {
                return index2;
            }
            return -1;
        }

        @Override
        public int findEnumIndex(String key) {
            // should be canonical so check equals first
            if (key1 == key) {
                return index1;
            }
            if (key2 == key) {
                return index2;
            }
            if (key1.equals(key)) {
                return index1;
            }
            if (key2.equals(key)) {
                return index2;
            }
            return -1;
        }

        @Override
        public Collection<String> getEnumValues() {
            return Arrays.asList(key1, key2);
        }
    }

    final static class Small3 extends EnumLookup
    {
        protected final String key1, key2, key3;
        protected final int index1, index2, index3;

        private Small3(String k1, int i1, String k2, int i2, String k3, int i3) {
            key1 = k1;
            index1 = i1;
            key2 = k2;
            index2 = i2;
            key3 = k3;
            index3 = i3;
        }

        @Override
        public String findEnumByIndex(int index) {
            if (index == index1) {
                return key1;
            }
            if (index == index2) {
                return key2;
            }
            if (index == index3) {
                return key3;
            }
            return null;
        }
        
        @Override
        public int findEnumIndex(SerializableString key0) {
            String key = key0.getValue();
            // should be canonical so check equals first
            if (key1 == key) {
                return index1;
            }
            if (key2 == key) {
                return index2;
            }
            if (key3 == key) {
                return index3;
            }
            return _findIndex2(key);
        }

        @Override
        public int findEnumIndex(String key) {
            if (key1 == key) {
                return index1;
            }
            if (key2 == key) {
                return index2;
            }
            if (key3 == key) {
                return index3;
            }
            return _findIndex2(key);
        }

        @Override
        public Collection<String> getEnumValues() {
            return Arrays.asList(key1, key2, key3);
        }

        private int _findIndex2(String key) {
            if (key1.equals(key)) {
                return index1;
            }
            if (key2.equals(key)) {
                return index2;
            }
            if (key3.equals(key)) {
                return index3;
            }
            return -1;
        }
    }

    /**
     * Raw mapping from keys to indices, optimized for fast access via
     * better memory efficiency. Hash area divide in three; main hash,
     * half-size secondary, followed by as-big-as-needed spillover.
     */
    final static class Big extends EnumLookup
    {
        private final int _hashMask, _spillCount;

        private final String[] _keys;
        private final int[] _indices;

        /**
         * For fields of type {@link FieldType#ENUM} with non-standard indexing,
         * mapping back from tag ids to enum names.
         */
        protected final LinkedHashMap<Integer,String> _enumsById;
        
        private Big(LinkedHashMap<Integer,String> byId,
                int hashMask, int spillCount, String[] keys, int[] indices)
        {
            _enumsById = byId;
            _hashMask = hashMask;
            _spillCount = spillCount;
            _keys = keys;
            _indices = indices;
        }

        public static Big construct(List<Map.Entry<String,Integer>> entries)
        {
            LinkedHashMap<Integer,String> byId = new LinkedHashMap<Integer,String>();
            
            // First: calculate size of primary hash area
            final int size = findSize(byId.size());
            final int mask = size-1;
            // and allocate enough to contain primary/secondary, expand for spillovers as need be
            int alloc = size + (size>>1);
            String[] keys = new String[alloc];
            int[] indices = new int[alloc];
            int spills = 0;

            for (Map.Entry<String,Integer> entry : entries) {
                String key = entry.getKey();
                int index = entry.getValue().intValue();

                byId.put(entry.getValue(), key);

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
                            indices = Arrays.copyOf(indices, indices.length + 4);
                        }
                    }
                }
                keys[slot] = key;
                indices[slot] = index;
            }
            return new Big(byId, mask, spills, keys, indices);
        }

        @Override
        public String findEnumByIndex(int index) {
            return _enumsById.get(index);
        }

        @Override
        public int findEnumIndex(SerializableString key0) {
            final String key = key0.getValue();
            int slot = key.hashCode() & _hashMask;
            String match = _keys[slot];
            if ((match == key) || key.equals(match)) {
                return _indices[slot];
            }
            if (match == null) {
                return -1;
            }
            // no? secondary?
            slot = (_hashMask+1) + (slot>>1);
            match = _keys[slot];
            if ((match == key) || key.equals(match)) {
                return _indices[slot];
            }
            // or spill?
            if (_spillCount > 0) {
                return _findFromSpill(key);
            }
            return -1;
        }

        @Override
        public int findEnumIndex(String key) {
            int slot = key.hashCode() & _hashMask;
            String match = _keys[slot];
            if ((match == key) || key.equals(match)) {
                return _indices[slot];
            }
            if (match == null) {
                return -1;
            }
            // no? secondary?
            slot = (_hashMask+1) + (slot>>1);
            match = _keys[slot];
            if ((match == key) || key.equals(match)) {
                return _indices[slot];
            }
            // or spill?
            if (_spillCount > 0) {
                return _findFromSpill(key);
            }
            return -1;
        }
        
        @Override
        public Collection<String> getEnumValues() {
            List<String> result = new ArrayList<String>(_hashMask+1);
            for (String str : _keys) {
                if (str != null) {
                    result.add(str);
                }
            }
            return result;
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

        private int _findFromSpill(String key) {
            int hashSize = _hashMask+1;
            int i = hashSize + (hashSize>>1);
            for (int end = i + _spillCount; i < end; ++i) {
                if (key.equals(_keys[i])) {
                    return _indices[i];
                }
            }
            return -1;
        }
    }
}
