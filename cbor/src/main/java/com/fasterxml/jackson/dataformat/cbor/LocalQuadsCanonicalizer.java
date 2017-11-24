package com.fasterxml.jackson.dataformat.cbor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Simplified static symbol table used instead of global quad-based canonicalizer
 * when we have smaller set of symbols (like properties of a POJO class).
 */
public final class LocalQuadsCanonicalizer
{
    private final static int MIN_HASH_SIZE = 8;

    /*
    /**********************************************************
    /* First, main hash area info
    /**********************************************************
     */

    /**
     * Primary hash information area: consists of <code>2 * _hashSize</code>
     * entries of 16 bytes (4 ints), arranged in a cascading lookup
     * structure (details of which may be tweaked depending on expected rates
     * of collisions).
     */
    private int[] _hashArea;

    /**
     * Number of slots for primary entries within {@link #_hashArea}; which is
     * at most <code>1/8</code> of actual size of the underlying array (4-int slots,
     * primary covers only half of the area; plus, additional area for longer
     * symbols after hash area).
     */
    private int _hashSize;

    /**
     * Offset within {@link #_hashArea} where secondary entries start
     */
    private int _secondaryStart;

    /**
     * Offset within {@link #_hashArea} where tertiary entries start
     */
    private int _tertiaryStart;
    
    /**
     * Constant that determines size of buckets for tertiary entries:
     * <code>1 &lt;&lt; _tertiaryShift</code> is the size, and shift value
     * is also used for translating from primary offset into
     * tertiary bucket (shift right by <code>4 + _tertiaryShift</code>).
     *<p>
     * Default value is 2, for buckets of 4 slots; grows bigger with
     * bigger table sizes.
     */
    private int _tertiaryShift;

    /**
     * Total number of Strings in the symbol table
     */
    private int _count;

    /**
     * Array that contains <code>String</code> instances matching
     * entries in {@link #_hashArea}.
     * Contains nulls for unused entries. Note that this size is twice
     * that of {@link #_hashArea}
     */
    private String[] _names;

    /*
    /**********************************************************
    /* Then information on collisions etc
    /**********************************************************
     */

    /**
     * Pointer to the offset within spill-over area where there is room
     * for more spilled over entries (if any).
     * Spill over area is within fixed-size portion of {@link #_hashArea}.
     */
    private int _spilloverEnd;

    /**
     * Offset within {@link #_hashArea} that follows main slots and contains
     * quads for longer names (13 bytes or longer), and points to the
     * first available int that may be used for appending quads of the next
     * long name.
     * Note that long name area follows immediately after the fixed-size
     * main hash area ({@link #_hashArea}).
     */
    private int _longNameOffset;

    /*
    /**********************************************************
    /* Life-cycle: constructors
    /**********************************************************
     */

    /**
     * Constructor used for creating per-<code>JsonFactory</code> "root"
     * symbol tables: ones used for merging and sharing common symbols
     * 
     * @param entryCount Number of Strings to contain
     * @param sz Size of logical hash area
     */
    private LocalQuadsCanonicalizer(int entryCount, int sz) {
        _count = 0;
        _hashSize = sz; // 8x, 4 ints per entry for main area, then sec/ter and spill over
        _hashArea = new int[sz << 3];

        _secondaryStart = sz << 2; // right after primary area (at 50%)
        _tertiaryStart = _secondaryStart + (_secondaryStart >> 1); // right after secondary
        _tertiaryShift = _calcTertiaryShift(sz);
        _spilloverEnd = _hashArea.length - sz; // start AND end the same, at 7/8, initially
        _longNameOffset = _hashSize; // and start of long name area is at end of initial area (to be expanded)

        _names = new String[sz * 2];
    }

    static int _calcTertiaryShift(int primarySlots)
    {
        // first: we only get 1/4 of slots of primary, to divide
        int tertSlots = (primarySlots) >> 2;
        // default is for buckets of 4 slots (each 4 ints, i.e. 1 << 4)
        if (tertSlots < 64) {
            return 4;
        }
        if (tertSlots <= 256) { // buckets of 8 slots (up to 256 == 32 x 8)
            return 5;
        }
        if (tertSlots <= 1024) { // buckets of 16 slots (up to 1024 == 64 x 16)
            return 6;
        }
        // and biggest buckets have 32 slots
        return 7;
    }

    /**
     * Factory method to call to create a symbol table instance with a
     * randomized seed value.
     */
    public static LocalQuadsCanonicalizer createEmpty(int entryCount) {
        int sz = entryCount;
        // Sanity check: let's now allow hash sizes below certain minimum value. This is size in entries
        if (sz < MIN_HASH_SIZE) {
            sz = MIN_HASH_SIZE;
        } else {
            // Also; size must be 2^N; otherwise hash algorithm won't work: let's just pad it up, if so
            if ((sz & (sz - 1)) != 0) { // only true if it's 2^N
                int curr = MIN_HASH_SIZE;
                while (curr < sz) {
                    curr += curr;
                }
                sz = curr;
            }
        }
        return new LocalQuadsCanonicalizer(entryCount, sz);
    }

    /*
    /**********************************************************
    /* API, mutators
    /**********************************************************
     */

    public String addName(String name) {
        byte[] ch = name.getBytes(StandardCharsets.UTF_8);
        int len = ch.length;

        if (len <= 12) {
            if (len <= 4) {
                return addName(name, _decodeLast(ch, 0, len));
            }
            int q1 = _decodeFull(ch, 0);
            if (len <= 8) {
                return addName(name, q1, _decodeLast(ch, 4, len-4));
            }
            return addName(name, q1, _decodeFull(ch, 4), _decodeLast(ch, 8, len-8));
        }
        int[] quads = quads(name);
        return addName(name, quads, quads.length);
    }

    public static int[] quads(String name) {
        final byte[] b = name.getBytes(StandardCharsets.UTF_8);
        final int len = b.length;
        int[] buf = new int[(len + 3) >> 2];

        int in = 0;
        int out = 0;
        int left = len;

        for (; left > 4; left -= 4) {
            buf[out++] = _decodeFull(b, in);
            in += 4;
        }
        buf[out++] = _decodeLast(b, in, left);
        return buf;
    }
    
    private final static int _decodeFull(byte[] b, int offset) {
        return (b[offset] << 24) + ((b[offset+1] & 0xFF) << 16)
                + ((b[offset+2] & 0xFF) << 8) + (b[offset+3] & 0xFF);
    }

    private final static int _decodeLast(byte[] b, int offset, int bytes) {
        // 22-Nov-2017, tatu: Padding apparently not used with fully binary field names,
        //     unlike with JSON. May or may not want to change this in future.
        int value = b[offset++] & 0xFF;
        switch (bytes) {
        case 4:
            value = (value << 8) | (b[offset]++ & 0xFF);
        case 3:
            value = (value << 8) | (b[offset]++ & 0xFF);
        case 2:
            value = (value << 8) | (b[offset]++ & 0xFF);
        }
        return value;
    }

    private String addName(String name, int q1) {
        int offset = _findOffsetForAdd(calcHash(q1));
        _hashArea[offset] = q1;
        _hashArea[offset+3] = 1;
        _names[offset >> 2] = name;
        ++_count;
        return name;
    }

    private String addName(String name, int q1, int q2) {
        int offset = _findOffsetForAdd(calcHash(q1, q2));
        _hashArea[offset] = q1;
        _hashArea[offset+1] = q2;
        _hashArea[offset+3] = 2;
        _names[offset >> 2] = name;
        ++_count;
        return name;
    }

    private String addName(String name, int q1, int q2, int q3) {
        int offset = _findOffsetForAdd(calcHash(q1, q2, q3));
        _hashArea[offset] = q1;
        _hashArea[offset+1] = q2;
        _hashArea[offset+2] = q3;
        _hashArea[offset+3] = 3;
        _names[offset >> 2] = name;
        ++_count;
        return name;
    }

    private String addName(String name, int[] q, int qlen)
    {
        switch (qlen) {
        case 1:
            return addName(name, q[0]);
        case 2:
            return addName(name, q[0], q[1]);
        case 3:
            return addName(name, q[0], q[1], q[2]);
        }
        final int hash = calcHash(q, qlen);
        int offset = _findOffsetForAdd(hash);

        _hashArea[offset] = hash;
        int longStart = _appendLongName(q, qlen);
        _hashArea[offset+1] = longStart;
        _hashArea[offset+3] = qlen;

        // plus add the actual String
        _names[offset >> 2] = name;

        ++_count;
        return name;
    }

    /**
     * Method called to find the location within hash table to add a new symbol in.
     */
    private int _findOffsetForAdd(int hash)
    {
        // first, check the primary:
        int offset = _calcOffset(hash);
        final int[] hashArea = _hashArea;
        if (hashArea[offset+3] == 0) {
            return offset;
        }
        // then secondary
        int offset2 = _secondaryStart + ((offset >> 3) << 2);
        if (hashArea[offset2+3] == 0) {
            return offset2;
        }
        // if not, tertiary?
        offset2 = _tertiaryStart + ((offset >> (_tertiaryShift + 2)) << _tertiaryShift);
        final int bucketSize = (1 << _tertiaryShift);
        for (int end = offset2 + bucketSize; offset2 < end; offset2 += 4) {
            if (hashArea[offset2+3] == 0) {
                return offset2;
            }
        }
        // and if even tertiary full, append at the end of spill area
        offset = _spilloverEnd;
        _spilloverEnd += 4;

//System.err.printf(" SPIll-over at x%X; start x%X; end x%X, hash %X\n", offset, _spilloverStart(), _hashArea.length, (hash & 0x7F));
        
        // one caveat: in the unlikely event if spill-over filling up,
        // check if that could be considered a DoS attack; handle appropriately
        // (NOTE: approximate for now; we could verify details if that becomes necessary)
        /* 31-Jul-2015, tatu: Note that spill-over area does NOT end at end of array,
         *   since "long names" area follows. Instead, need to calculate from hash size.
         */
        final int end = (_hashSize << 3);
        if (_spilloverEnd >= end) {
            // !!! TODO
        }
        return offset;
    }

    private int _appendLongName(int[] quads, int qlen)
    {
        int start = _longNameOffset;
        
        // note: at this point we must already be shared. But may not have enough space
        if ((start + qlen) > _hashArea.length) {
            // try to increment in reasonable chunks; at least space that we need
            int toAdd = (start + qlen) - _hashArea.length;
            // but at least 1/8 of regular hash area size or 16kB (whichever smaller)
            int minAdd = Math.min(4096, _hashSize);

            int newSize = _hashArea.length + Math.max(toAdd, minAdd);
            _hashArea = Arrays.copyOf(_hashArea, newSize);
        }
        System.arraycopy(quads, 0, _hashArea, start, qlen);
        _longNameOffset += qlen;
        return start;
    }
    
    /*
    /**********************************************************
    /* API, accessors, mostly for Unit Tests
    /**********************************************************
     */

    public int size() { return _count; }

    public int bucketCount() { return _hashSize; }

    // For tests
    public int primaryCount()
    {
        int count = 0;
        for (int offset = 3, end = _secondaryStart; offset < end; offset += 4) {
            if (_hashArea[offset] != 0) {
                ++count;
            }
        }
        return count;
    }

    // For tests
    public int secondaryCount() {
        int count = 0;
        int offset = _secondaryStart + 3;
        for (int end = _tertiaryStart; offset < end; offset += 4) {
            if (_hashArea[offset] != 0) {
                ++count;
            }
        }
        return count;
    }

    // For tests
    public int tertiaryCount() {
        int count = 0;
        int offset = _tertiaryStart + 3; // to 1.5x, starting point of tertiary
        for (int end = offset + _hashSize; offset < end; offset += 4) {
            if (_hashArea[offset] != 0) {
                ++count;
            }
        }
        return count;
    }

    // For tests
    public int spilloverCount() {
        // difference between spillover end, start, divided by 4 (four ints per slot)
        return (_spilloverEnd - _spilloverStart()) >> 2;
    }

    // For tests
    public int totalCount() {
        int count = 0;
        for (int offset = 3, end = (_hashSize << 3); offset < end; offset += 4) {
            if (_hashArea[offset] != 0) {
                ++count;
            }
        }
        return count;
    }

    /*
    /**********************************************************
    /* Public API, accessing symbols
    /**********************************************************
     */

    public String findName(int q1)
    {
        int offset = _calcOffset(calcHash(q1));
        // first: primary match?
        final int[] hashArea = _hashArea;

        int len = hashArea[offset+3];

        if (len == 1) {
            if (hashArea[offset] == q1) {
                return _names[offset >> 2];
            }
        } else if (len == 0) { // empty slot; unlikely but avoid further lookups if so
            return null;
        }
        // secondary? single slot shared by N/2 primaries
        int offset2 = _secondaryStart + ((offset >> 3) << 2);

        len = hashArea[offset2+3];

        if (len == 1) {
            if (hashArea[offset2] == q1) {
                return _names[offset2 >> 2];
            }
        } else if (len == 0) { // empty slot; unlikely but avoid further lookups if so
            return null;
        }

        // tertiary lookup & spillovers best to offline
        return _findSecondary(offset, q1);
    }

    public String findName(int q1, int q2)
    {
        int offset = _calcOffset(calcHash(q1, q2));

        final int[] hashArea = _hashArea;
        int len = hashArea[offset+3];

        if (len == 2) {
            if ((q1 == hashArea[offset]) && (q2 == hashArea[offset+1])) {
                return _names[offset >> 2];
            }
        } else if (len == 0) { // empty slot; unlikely but avoid further lookups if so
            return null;
        }
        // secondary?
        int offset2 = _secondaryStart + ((offset >> 3) << 2);

        len = hashArea[offset2+3];

        if (len == 2) {
            if ((q1 == hashArea[offset2]) && (q2 == hashArea[offset2+1])) {
                return _names[offset2 >> 2];
            }
        } else if (len == 0) { // empty slot? Short-circuit if no more spillovers
            return null;
        }
        return _findSecondary(offset, q1, q2);
    }

    public String findName(int q1, int q2, int q3)
    {
        int offset = _calcOffset(calcHash(q1, q2, q3));
        final int[] hashArea = _hashArea;
        int len = hashArea[offset+3];

        if (len == 3) {
            if ((q1 == hashArea[offset]) && (hashArea[offset+1] == q2) && (hashArea[offset+2] == q3)) {
                return _names[offset >> 2];
            }
        } else if (len == 0) { // empty slot; unlikely but avoid further lookups if so
            return null;
        }
        // secondary?
        int offset2 = _secondaryStart + ((offset >> 3) << 2);

        len = hashArea[offset2+3];

        if (len == 3) {
            if ((q1 == hashArea[offset2]) && (hashArea[offset2+1] == q2) && (hashArea[offset2+2] == q3)) {
                return _names[offset2 >> 2];
            }
        } else if (len == 0) { // empty slot? Short-circuit if no more spillovers
            return null;
        }
        return _findSecondary(offset, q1, q2, q3);
    }

    public String findName(int[] q, int qlen)
    {
        // This version differs significantly, because longer names do not fit within cell.
        // Rather, they contain hash in main slot, and offset+length to extension area
        // that contains actual quads.
        if (qlen < 4) { // another sanity check
            switch (qlen) {
            case 3:
                return findName(q[0], q[1], q[2]);
            case 2:
                return findName(q[0], q[1]);
            case 1:
                return findName(q[0]);
            default: // if 0 ever passed
                return "";
            }
        }
        final int hash = calcHash(q, qlen);
        int offset = _calcOffset(hash);

        final int[] hashArea = _hashArea;

        final int len = hashArea[offset+3];
        
        if ((hash == hashArea[offset]) && (len == qlen)) {
            // probable but not guaranteed: verify
            if (_verifyLongName(q, qlen, hashArea[offset+1])) {
                return _names[offset >> 2];
            }
        }
        if (len == 0) { // empty slot; unlikely but avoid further lookups if so
            return null;
        }
        // secondary?
        int offset2 = _secondaryStart + ((offset >> 3) << 2);

        final int len2 = hashArea[offset2+3];
        if ((hash == hashArea[offset2]) && (len2 == qlen)) {
            if (_verifyLongName(q, qlen, hashArea[offset2+1])) {
                return _names[offset2 >> 2];
            }
        }
        return _findSecondary(offset, hash, q, qlen);
    }
    
    private final int _calcOffset(int hash)
    {
        int ix = hash & (_hashSize-1);
        // keeping in mind we have 4 ints per entry
        return (ix << 2);
    }

    /*
    /**********************************************************
    /* Access from spill-over areas
    /**********************************************************
     */

    private String _findSecondary(int origOffset, int q1)
    {
        // tertiary area division is dynamic. First; its size is N/4 compared to
        // primary hash size; and offsets are for 4 int slots. So to get to logical
        // index would shift by 4. But! Tertiary area is further split into buckets,
        // determined by shift value. And finally, from bucket back into physical offsets
        int offset = _tertiaryStart + ((origOffset >> (_tertiaryShift + 2)) << _tertiaryShift);
        final int[] hashArea = _hashArea;
        final int bucketSize = (1 << _tertiaryShift);
        for (int end = offset + bucketSize; offset < end; offset += 4) {
            int len = hashArea[offset+3];
            if ((q1 == hashArea[offset]) && (1 == len)) {
                return _names[offset >> 2];
            }
            if (len == 0) {
                return null;
            }
        }
        // but if tertiary full, check out spill-over area as last resort
        // shared spillover starts at 7/8 of the main hash area
        // (which is sized at 2 * _hashSize), so:
        for (offset = _spilloverStart(); offset < _spilloverEnd; offset += 4) {
            if ((q1 == hashArea[offset]) && (1 == hashArea[offset+3])) {
                return _names[offset >> 2];
            }
        }
        return null;
    }

    private String _findSecondary(int origOffset, int q1, int q2)
    {
        int offset = _tertiaryStart + ((origOffset >> (_tertiaryShift + 2)) << _tertiaryShift);
        final int[] hashArea = _hashArea;

        final int bucketSize = (1 << _tertiaryShift);
        for (int end = offset + bucketSize; offset < end; offset += 4) {
            int len = hashArea[offset+3];
            if ((q1 == hashArea[offset]) && (q2 == hashArea[offset+1]) && (2 == len)) {
                return _names[offset >> 2];
            }
            if (len == 0) {
                return null;
            }
        }
        for (offset = _spilloverStart(); offset < _spilloverEnd; offset += 4) {
            if ((q1 == hashArea[offset]) && (q2 == hashArea[offset+1]) && (2 == hashArea[offset+3])) {
                return _names[offset >> 2];
            }
        }
        return null;
    }

    private String _findSecondary(int origOffset, int q1, int q2, int q3)
    {
        int offset = _tertiaryStart + ((origOffset >> (_tertiaryShift + 2)) << _tertiaryShift);
        final int[] hashArea = _hashArea;

        final int bucketSize = (1 << _tertiaryShift);
        for (int end = offset + bucketSize; offset < end; offset += 4) {
            int len = hashArea[offset+3];
            if ((q1 == hashArea[offset]) && (q2 == hashArea[offset+1]) && (q3 == hashArea[offset+2]) && (3 == len)) {
                return _names[offset >> 2];
            }
            if (len == 0) {
                return null;
            }
        }
        for (offset = _spilloverStart(); offset < _spilloverEnd; offset += 4) {
            if ((q1 == hashArea[offset]) && (q2 == hashArea[offset+1]) && (q3 == hashArea[offset+2])
                    && (3 == hashArea[offset+3])) {
                return _names[offset >> 2];
            }
        }
        return null;
    }

    private String _findSecondary(int origOffset, int hash, int[] q, int qlen)
    {
        int offset = _tertiaryStart + ((origOffset >> (_tertiaryShift + 2)) << _tertiaryShift);
        final int[] hashArea = _hashArea;

        final int bucketSize = (1 << _tertiaryShift);
        for (int end = offset + bucketSize; offset < end; offset += 4) {
            int len = hashArea[offset+3];
            if ((hash == hashArea[offset]) && (qlen == len)) {
                if (_verifyLongName(q, qlen, hashArea[offset+1])) {
                    return _names[offset >> 2];
                }
            }
            if (len == 0) {
                return null;
            }
        }
        for (offset = _spilloverStart(); offset < _spilloverEnd; offset += 4) {
            if ((hash == hashArea[offset]) && (qlen == hashArea[offset+3])) {
                if (_verifyLongName(q, qlen, hashArea[offset+1])) {
                    return _names[offset >> 2];
                }
            }
        }
        return null;
    }
    
    private boolean _verifyLongName(int[] q, int qlen, int spillOffset)
    {
        final int[] hashArea = _hashArea;
        // spillOffset assumed to be physical index right into quad string
        int ix = 0;

        switch (qlen) {
        default:
            return _verifyLongName2(q, qlen, spillOffset);
        case 8:
            if (q[ix++] != hashArea[spillOffset++]) return false;
        case 7:
            if (q[ix++] != hashArea[spillOffset++]) return false;
        case 6:
            if (q[ix++] != hashArea[spillOffset++]) return false;
        case 5:
            if (q[ix++] != hashArea[spillOffset++]) return false;
        case 4: // always at least 4
            if (q[ix++] != hashArea[spillOffset++]) return false;
            if (q[ix++] != hashArea[spillOffset++]) return false;
            if (q[ix++] != hashArea[spillOffset++]) return false;
            if (q[ix++] != hashArea[spillOffset++]) return false;
        }
        return true;
    }

    private boolean _verifyLongName2(int[] q, int qlen, int spillOffset)
    {
        int ix = 0;
        do {
            if (q[ix++] != _hashArea[spillOffset++]) {
                return false;
            }
        } while (ix < qlen);
        return true;
    }

    /*
    /**********************************************************
    /* Hash calculation
    /**********************************************************
     */

    // // Copied straight frmo big quads canonicalizer: look comments there
    
    private final static int MULT = 33;
    private final static int MULT2 = 65599;
    private final static int MULT3 = 31;
    
    public int calcHash(int q1)
    {
        int hash = q1;
        hash += (hash >>> 16);
        hash ^= (hash << 3);
        hash += (hash >>> 12);
        return hash;
    }

    public int calcHash(int q1, int q2)
    {
        int hash = q1;

        hash += (hash >>> 15);
        hash ^= (hash >>> 9);
        hash += (q2 * MULT);
        hash += (hash >>> 16);
        hash ^= (hash >>> 4);
        hash += (hash << 3);
        
        return hash;
    }

    public int calcHash(int q1, int q2, int q3)
    { // use same algorithm as multi-byte, tested to work well
        int hash = q1;
        hash += (hash >>> 9);
        hash *= MULT3;
        hash += q2;
        hash *= MULT;
        hash += (hash >>> 15);
        hash ^= q3;
        // 26-Mar-2015, tatu: As per two-quad case, a short shift seems to help more here
        hash += (hash >>> 4);

        hash += (hash >>> 15);
        hash ^= (hash << 9);

        return hash;
    }

    public int calcHash(int[] q, int qlen)
    {
        if (qlen < 4) {
            throw new IllegalArgumentException();
        }
        /* And then change handling again for "multi-quad" case; mostly
         * to make calculation of collisions less fun. For example,
         * add seed bit later in the game, and switch plus/xor around,
         * use different shift lengths.
         */
        int hash = q[0];
        hash += (hash >>> 9);
        hash += q[1];
        hash += (hash >>> 15);
        hash *= MULT;
        hash ^= q[2];
        hash += (hash >>> 4);

        for (int i = 3; i < qlen; ++i) {
            int next = q[i];
            next = next ^ (next >> 21);
            hash += next;
        }
        hash *= MULT2;
        
        // and finally shuffle some more once done
        hash += (hash >>> 19);
        hash ^= (hash << 5);
        return hash;
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */

    /**
     * Helper method that calculates start of the spillover area
     */
    private final int _spilloverStart() {
        // we'll need slot at 1.75x of hashSize, but with 4-ints per slot.
        // So basically multiply by 7 (i.e. shift to multiply by 8 subtract 1)
        int offset = _hashSize;
        return (offset << 3) - offset;
    }
}
