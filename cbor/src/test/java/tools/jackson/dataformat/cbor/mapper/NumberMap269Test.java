package tools.jackson.dataformat.cbor.mapper;

import java.util.*;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.cbor.CBORTestBase;

// [dataformats-binary#269]
public class NumberMap269Test extends CBORTestBase
{
    static class TestData269 {
        Map<Long, String> map;

        public TestData269 setMap(Map<Long, String> map) {
            this.map = map;
            return this;
        }

        public Map<Long, String> getMap() {
            return map;
        }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = cborMapper();

    // [dataformats-binary#269]
    public void testInt32BoundaryWithMapKey() throws Exception
    {
        // First, with specific reported combo:
        _testInt32BoundaryWithMapKey(4294967296L, -4294967296L);

        // and then systematically couple of others (actually overlapping but...)
        final long MAX_POS_UINT32 = 0xFFFFFFFFL;
        final long MAX_POS_UINT32_PLUS_1 = MAX_POS_UINT32 + 1L;

        _testInt32BoundaryWithMapKey(MAX_POS_UINT32, -MAX_POS_UINT32);
        _testInt32BoundaryWithMapKey(MAX_POS_UINT32_PLUS_1,
                -MAX_POS_UINT32_PLUS_1);

        _testInt32BoundaryWithMapKey(MAX_POS_UINT32_PLUS_1 + 1L,
                -MAX_POS_UINT32_PLUS_1 - 1L);
    }

    private void _testInt32BoundaryWithMapKey(long key1, long key2) throws Exception
    {
        Map<Long, String> map = new LinkedHashMap<>();
        map.put(key1, "hello");
        map.put(key2, "world");
        TestData269 input = new TestData269().setMap(map);

        byte[] cborDoc = MAPPER.writeValueAsBytes(input);

        TestData269 result = MAPPER.readValue(cborDoc, TestData269.class);

        assertEquals(input.map, result.map);
    }
}
