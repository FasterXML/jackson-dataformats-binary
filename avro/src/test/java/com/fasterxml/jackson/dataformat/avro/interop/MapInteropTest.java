package com.fasterxml.jackson.dataformat.avro.interop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.avro.reflect.Stringable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class MapInteropTest extends InteropTestBase<MapInteropTest.MapsObject, MapInteropTest.MapsObject> {
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DummyRecord {
        private String name;
        private int    value;
    }

    @Data
    public static class MapsObject {
        // varied dummy data, but consistent
        private static Random random = new Random(123456789);

        @SneakyThrows
        public static MapsObject dummy() {
            MapsObject object = new MapsObject();
            object.mappedByteArray = new HashMap<>();
            object.mappedByteWrapperArray = new HashMap<>();
            object.mappedShortArray = new HashMap<>();
            object.mappedShortWrapperArray = new HashMap<>();
            object.mappedCharArray = new HashMap<>();
            object.mappedCharWrapperArray = new HashMap<>();
            object.mappedIntArray = new HashMap<>();
            object.mappedIntWrapperArray = new HashMap<>();
            object.mappedLongArray = new HashMap<>();
            object.mappedLongWrapperArray = new HashMap<>();
            object.mappedFloatArray = new HashMap<>();
            object.mappedFloatWrapperArray = new HashMap<>();
            object.mappedDoubleArray = new HashMap<>();
            object.mappedDoubleWrapperArray = new HashMap<>();
            for (int m = 0; m <= 10; m++) {
                byte[] byteArray = new byte[10];
                random.nextBytes(byteArray);
                object.mappedByteArray.put("bytes " + m, byteArray);
                Byte[] byteWrapperArray = new Byte[]{
                    1, (byte) m, 3, -4, 5, (byte) random.nextInt(), (byte) random.nextInt(), (byte) random.nextInt()
                };
                object.mappedByteWrapperArray.put("byte wrapper " + m, byteWrapperArray);
                short[] shortArray = new short[]{
                    1, 2, 3, -4, 5, (short) random.nextInt(), (short) random.nextInt(), (short) random.nextInt(), (short) random.nextInt()
                };
                object.mappedShortArray.put("shorts " + m, shortArray);
                Short[] shortWrapperArray = new Short[]{
                    6, 7, 8, -9, 0, (short) random.nextInt(), (short) random.nextInt(), (short) random.nextInt(), (short) random.nextInt()
                };
                object.mappedShortWrapperArray.put("short wrapper " + m, shortWrapperArray);
                char[] charArray = new char[]{
                    '1', '2', '3', '4', '5', (char) random.nextInt(), (char) random.nextInt(), (char) random.nextInt()
                };
                object.mappedCharArray.put("chars " + m, charArray);
                Character[] charWrapperArray = new Character[]{
                    '6', '7', '8', '9', '0', (char) random.nextInt(), (char) random.nextInt(), (char) random.nextInt()
                };
                object.mappedCharWrapperArray.put("char wrapper " + m, charWrapperArray);
                int[] intArray = new int[]{
                    1, 2, 3, -4, 5, random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt()
                };
                object.mappedIntArray.put("int " + m, intArray);
                Integer[] intWrapperArray = new Integer[]{
                    6, 7, 8, -9, 0, random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt()
                };
                object.mappedIntWrapperArray.put("int wrapper " + m, intWrapperArray);
                long[] longArray = new long[]{
                    1L, 2L, 3L, -4L, 5L, random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()
                };
                object.mappedLongArray.put("longs " + m, longArray);
                Long[] longWrapperArray = new Long[]{
                    6L, 7L, 8L, -9L, 0L, random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()
                };
                object.mappedLongWrapperArray.put("long wrapper " + m, longWrapperArray);
                float[] floatArray = new float[]{
                    1F, 2F, 3F, -4F, 5F, random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat()
                };
                object.mappedFloatArray.put("floats " + m, floatArray);
                Float[] floatWrapperArray = new Float[]{
                    6F, 7F, 8F, -9F, 0F, random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat()
                };
                object.mappedFloatWrapperArray.put("float wrapper " + m, floatWrapperArray);
                double[] doubleArray = new double[]{
                    1D, 2D, 3D, -4D, 5D, random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble()
                };
                object.mappedDoubleArray.put("doubles " + m, doubleArray);
                Double[] doubleWrapperArray = new Double[]{
                    6D, 7D, 8D, -9D, 0D, random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble()
                };
                object.mappedDoubleWrapperArray.put("double wrapper " + m, doubleWrapperArray);
            }
            object.mappedEmptyRecordArray = Collections.singletonMap("test record map", new DummyRecord[0]);
            object.mappedEmptyScalarArray = Collections.singletonMap("test scalar map", new int[0]);
            object.mappedStringArray = new HashMap<>();
            for (int m = 0; m <= random.nextInt(10); m++) {
                String[] recordArray = new String[10];
                for (int i = 0; i < recordArray.length; i++) {
                    recordArray[i] = Long.toString(random.nextLong());
                }
                object.mappedStringArray.put(Integer.toString(m), recordArray);
            }
            object.mappedRecordArray = new HashMap<>();
            for (int m = 0; m <= random.nextInt(10); m++) {
                DummyRecord[] recordArray = new DummyRecord[10];
                for (int i = 0; i < recordArray.length; i++) {
                    recordArray[i] = new DummyRecord(Double.toString(random.nextDouble()), random.nextInt());
                }
                object.mappedRecordArray.put(Integer.toString(m), recordArray);
            }
            object.mappedRecordList = new HashMap<>();
            for (int m = 0; m <= random.nextInt(10); m++) {
                List<DummyRecord> recordList = new ArrayList<>();
                for (int i = 0; i <= random.nextInt(10); i++) {
                    recordList.add(new DummyRecord(Double.toString(random.nextDouble()), random.nextInt()));
                }
                object.mappedRecordList.put(Integer.toString(m), recordList);
            }
            object.mappedRecordArrayList = new HashMap<>();
            for (int m = 0; m <= random.nextInt(10); m++) {
                ArrayList<DummyRecord> recordArrayList = new ArrayList<>();
                for (int i = 0; i <= random.nextInt(10); i++) {
                    recordArrayList.add(new DummyRecord(Double.toString(random.nextDouble()), random.nextInt()));
                }
                object.mappedRecordArrayList.put(Integer.toString(m), recordArrayList);
            }
            object.mappedRecordLinkedList = new HashMap<>();
            for (int m = 0; m <= random.nextInt(10); m++) {
                LinkedList<DummyRecord> recordLinkedList = new LinkedList<>();
                for (int i = 0; i <= random.nextInt(10); i++) {
                    recordLinkedList.add(new DummyRecord(Double.toString(random.nextDouble()), random.nextInt()));
                }
                object.mappedRecordLinkedList.put(Integer.toString(m), recordLinkedList);
            }
            object.bigDecimalKeyedMap = new HashMap<>();
            object.bigDecimalKeyedMap.put(new BigDecimal("123456789456123456789.123654987456321"), new DummyRecord("first value", -1));
            object.bigDecimalKeyedMap.put(new BigDecimal("1.23456789456123456789123654987456321"), new DummyRecord("second value", -13468));
            object.bigDecimalKeyedMap.put(new BigDecimal("12345678945612345678912365498745632.1"),
                                          new DummyRecord("third value", 914382643)
            );
            object.bigIntegerKeyedMap = new HashMap<>();
            object.bigIntegerKeyedMap.put(new BigInteger("123456789456123456789123654987456321"),
                                          new DummyRecord("first value", 914434643)
            );
            object.bigIntegerKeyedMap.put(new BigInteger("123457982649637926496149814987456321"),
                                          new DummyRecord("second value", 919128643)
            );
            object.bigIntegerKeyedMap.put(new BigInteger("123452748469482408347850214862782721"),
                                          new DummyRecord("third value", 914124863)
            );
            object.bigIntegerKeyedMap.put(new BigInteger("478627862478620478047864087277456321"),
                                          new DummyRecord("fourth value", 914421863)
            );
            object.uriKeyedMap = new HashMap<>();
            object.uriKeyedMap.put(new URI("http://google.com"), new DummyRecord("first entry", 1));
            object.uriKeyedMap.put(new URI("http://fasterxml.com"), new DummyRecord("second entry", -2));
            object.uriKeyedMap.put(new URI("http://github.com"), new DummyRecord("third entry", 1348796));
            object.uriKeyedMap.put(new URI("http://google.com"), new DummyRecord("fourth entry", Integer.MIN_VALUE));
            object.urlKeyedMap = new HashMap<>();
            object.urlKeyedMap.put(new URL("http://google.com"), new DummyRecord("first entry", 1));
            object.urlKeyedMap.put(new URL("http://fasterxml.com"), new DummyRecord("second entry", -2));
            object.urlKeyedMap.put(new URL("http://github.com"), new DummyRecord("third entry", 1348796));
            object.urlKeyedMap.put(new URL("http://google.com"), new DummyRecord("fourth entry", Integer.MIN_VALUE));
            object.fileKeyedMap = new HashMap<>();
            object.fileKeyedMap.put(new File("/first/file"), new DummyRecord("first entry", 1));
            object.fileKeyedMap.put(new File("/second/file"), new DummyRecord("second entry", -2));
            object.fileKeyedMap.put(new File("/third/file"), new DummyRecord("third entry", 1348796));
            object.fileKeyedMap.put(new File("/fourth/file"), new DummyRecord("fourth entry", Integer.MIN_VALUE));
            object.stringableKeyedMap = new HashMap<>();
            object.stringableKeyedMap.put(new StringablePojo("test one"), new DummyRecord("value 1", 1));
            object.stringableKeyedMap.put(new StringablePojo("test two"), new DummyRecord("value 2", -6514));
            object.stringableKeyedMap.put(new StringablePojo("test three"), new DummyRecord("value 3", 1458714));
            object.stringableKeyedMap.put(new StringablePojo("test four"), new DummyRecord("value 4", 61489341));
            return object;
        }

        // Primitive array values
        private Map<String, byte[]>                  mappedByteArray;
        private Map<String, short[]>                 mappedShortArray;
        private Map<String, char[]>                  mappedCharArray;
        private Map<String, int[]>                   mappedIntArray;
        private Map<String, long[]>                  mappedLongArray;
        private Map<String, float[]>                 mappedFloatArray;
        private Map<String, double[]>                mappedDoubleArray;
        private Map<String, int[]>                   mappedEmptyScalarArray;
        // Primitive wrapper array values
        private Map<String, String[]>                mappedStringArray;
        private Map<String, Byte[]>                  mappedByteWrapperArray;
        private Map<String, Short[]>                 mappedShortWrapperArray;
        private Map<String, Character[]>             mappedCharWrapperArray;
        private Map<String, Integer[]>               mappedIntWrapperArray;
        private Map<String, Long[]>                  mappedLongWrapperArray;
        private Map<String, Float[]>                 mappedFloatWrapperArray;
        private Map<String, Double[]>                mappedDoubleWrapperArray;
        // Record values
        private Map<String, DummyRecord[]>           mappedEmptyRecordArray;
        private Map<String, DummyRecord[]>           mappedRecordArray;
        // List values
        private Map<String, List<DummyRecord>>       mappedRecordList;
        private Map<String, ArrayList<DummyRecord>>  mappedRecordArrayList;
        private Map<String, LinkedList<DummyRecord>> mappedRecordLinkedList;
        // Non-String keyed maps:
        private Map<BigInteger, DummyRecord>         bigIntegerKeyedMap;
        private Map<BigDecimal, DummyRecord>         bigDecimalKeyedMap;
        private Map<URI, DummyRecord>                uriKeyedMap;
        private Map<URL, DummyRecord>                urlKeyedMap;
        private Map<File, DummyRecord>               fileKeyedMap;
        private Map<StringablePojo, DummyRecord>     stringableKeyedMap;
        //TODO EnumMap
        //TODO TreeMap
        //TODO HashMap
        //TODO Maps of Maps
        //TODO Maps of Records of Maps
    }

    @Data
    @Stringable
    public static class StringablePojo {
        private final String value;

        public String toString() {
            return value;
        }
    }

    @Parameterized.Parameters(name = "{4}")
    public static Object[][] getParameters() {
        return getParameters(MapsObject.class, MapsObject.dummy(), MapsObject.class);
    }

    private <K, V> void compareMaps(Map<K, V> first, Map<K, V> second) {
        assertTrue("Actual did not contain all the expected keys", first.keySet().containsAll(second.keySet()));
        assertTrue(second.keySet().containsAll(first.keySet()));
        for (K key : first.keySet()) {
            assertThat(key.toString(), first.get(key), is(equalTo(second.get(key))));
        }
    }

    @Test
    public void testBigDecimalKeyedMap() {
        compareMaps(actual.bigDecimalKeyedMap, expected.bigDecimalKeyedMap);
    }

    @Test
    public void testBigIntegerKeyedMap() {
        compareMaps(actual.bigIntegerKeyedMap, expected.bigIntegerKeyedMap);
    }

    @Test
    public void testFileKeyedMap() {
        compareMaps(actual.fileKeyedMap, expected.fileKeyedMap);
    }

    @Test
    public void testMappedArrayList() {
        compareMaps(actual.mappedRecordArrayList, expected.mappedRecordArrayList);
    }

    @Test
    public void testMappedByteArray() {
        compareMaps(actual.mappedByteArray, expected.mappedByteArray);
    }

    @Test
    public void testMappedByteWrapper() {
        compareMaps(actual.mappedByteWrapperArray, expected.mappedByteWrapperArray);
    }

    @Test
    public void testMappedCharacterArray() {
        compareMaps(actual.mappedCharArray, expected.mappedCharArray);
    }

    @Test
    public void testMappedCharacterWrapper() {
        compareMaps(actual.mappedCharWrapperArray, expected.mappedCharWrapperArray);
    }

    @Test
    public void testMappedDoubleArray() {
        compareMaps(actual.mappedDoubleArray, expected.mappedDoubleArray);
    }

    @Test
    public void testMappedDoubleWrapper() {
        compareMaps(actual.mappedDoubleWrapperArray, expected.mappedDoubleWrapperArray);
    }

    @Test
    public void testMappedEmptyRecordArray() {
        compareMaps(actual.mappedEmptyRecordArray, expected.mappedEmptyRecordArray);
    }

    @Test
    public void testMappedEmptyScalarArray() {
        compareMaps(actual.mappedEmptyScalarArray, expected.mappedEmptyScalarArray);
    }

    @Test
    public void testMappedFloatArray() {
        compareMaps(actual.mappedFloatArray, expected.mappedFloatArray);
    }

    @Test
    public void testMappedFloatWrapper() {
        compareMaps(actual.mappedFloatWrapperArray, expected.mappedFloatWrapperArray);
    }

    @Test
    public void testMappedIntArray() {
        compareMaps(actual.mappedIntArray, expected.mappedIntArray);
    }

    @Test
    public void testMappedIntWrapper() {
        compareMaps(actual.mappedIntWrapperArray, expected.mappedIntWrapperArray);
    }

    @Test
    public void testMappedLinkedList() {
        compareMaps(actual.mappedRecordLinkedList, expected.mappedRecordLinkedList);
    }

    @Test
    public void testMappedList() {
        compareMaps(actual.mappedRecordList, expected.mappedRecordList);
    }

    @Test
    public void testMappedLongArray() {
        compareMaps(actual.mappedLongArray, expected.mappedLongArray);
    }

    @Test
    public void testMappedLongWrapper() {
        compareMaps(actual.mappedLongWrapperArray, expected.mappedLongWrapperArray);
    }

    @Test
    public void testMappedRecordArray() {
        compareMaps(actual.mappedRecordArray, expected.mappedRecordArray);
    }

    @Test
    public void testMappedShortArray() {
        compareMaps(actual.mappedShortArray, expected.mappedShortArray);
    }

    @Test
    public void testMappedShortWrapper() {
        compareMaps(actual.mappedShortWrapperArray, expected.mappedShortWrapperArray);
    }

    @Test
    public void testMappedStringArray() {
        compareMaps(actual.mappedStringArray, expected.mappedStringArray);
    }

    @Test
    public void testStringableKeyedMap() {
        compareMaps(actual.stringableKeyedMap, expected.stringableKeyedMap);
    }

    @Test
    public void testUriKeyedMap() {
        compareMaps(actual.uriKeyedMap, expected.uriKeyedMap);
    }

    @Test
    public void testUrlKeyedMap() {
        compareMaps(actual.urlKeyedMap, expected.urlKeyedMap);
    }
}
