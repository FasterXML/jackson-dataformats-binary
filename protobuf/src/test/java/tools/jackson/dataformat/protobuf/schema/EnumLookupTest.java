package tools.jackson.dataformat.protobuf.schema;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Tests for {@link EnumLookup}, in particular that the "big" variant sizes its
 * hash area from the actual entry count (it used to always size for 0 entries,
 * pushing nearly everything into the linear-scan spill area).
 */
public class EnumLookupTest
{
    @Test
    public void testBigLookupResolvesAllEntries()
    {
        final int count = 100;
        EnumLookup lookup = EnumLookup.construct(_enumDef(count));
        for (int i = 0; i < count; ++i) {
            String name = "VALUE_"+i;
            assertEquals(i, lookup.findEnumIndex(name), name);
            assertEquals(name, lookup.findEnumByIndex(i));
        }
        assertEquals(-1, lookup.findEnumIndex("NO_SUCH_VALUE"));
        assertEquals(count, lookup.getEnumValues().size());
    }

    @Test
    public void testBigLookupHashAreaSizedFromEntryCount()
    {
        final int count = 100;
        EnumLookup.Big lookup = assertInstanceOf(EnumLookup.Big.class,
                EnumLookup.construct(_enumDef(count)));
        // With correct sizing the primary hash area holds 128 slots; the old code
        // sized for 0 entries (8 primary + 4 secondary slots), so nearly every
        // entry ended up in the linearly-scanned overflow area.
        assertEquals(128, lookup.hashArea());
        if (lookup.spillCount() >= (count / 2)) {
            throw new AssertionError("Too many spill-over entries: "+lookup.spillCount());
        }
    }

    private ProtobufEnum _enumDef(int count) {
        Map<String,Integer> values = new LinkedHashMap<>();
        for (int i = 0; i < count; ++i) {
            values.put("VALUE_"+i, i);
        }
        return new ProtobufEnum("BigEnum", values, true);
    }
}
