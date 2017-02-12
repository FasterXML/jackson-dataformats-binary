package com.fasterxml.jackson.dataformat.avro.interop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class ArrayInteropTest extends InteropTestBase<ArrayInteropTest.ArraysObject, ArrayInteropTest.ArraysObject> {
    @Data
    public static class ArraysObject {
        // varied dummy data, but consistent
        private static Random random = new Random(123456789);

        public static ArraysObject dummy() {
            ArraysObject object = new ArraysObject();
            object.byteArray = new byte[10];
            random.nextBytes(object.byteArray);
            object.byteWrapperArray = new Byte[]{
                1, 2, 3, -4, 5, (byte) random.nextInt(), (byte) random.nextInt(), (byte) random.nextInt(), (byte) random.nextInt()
            };
            object.shortArray = new short[]{
                1, 2, 3, -4, 5, (short) random.nextInt(), (short) random.nextInt(), (short) random.nextInt(), (short) random.nextInt()
            };
            object.shortWrapperArray = new Short[]{
                6, 7, 8, -9, 0, (short) random.nextInt(), (short) random.nextInt(), (short) random.nextInt(), (short) random.nextInt()
            };
            object.charArray = new char[]{
                '1', '2', '3', '4', '5', (char) random.nextInt(), (char) random.nextInt(), (char) random.nextInt(), (char) random.nextInt()
            };
            object.charWrapperArray = new Character[]{
                '6', '7', '8', '9', '0', (char) random.nextInt(), (char) random.nextInt(), (char) random.nextInt(), (char) random.nextInt()
            };
            object.intArray = new int[]{
                1, 2, 3, -4, 5, random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt()
            };
            object.intWrapperArray = new Integer[]{
                6, 7, 8, -9, 0, random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt()
            };
            object.longArray = new long[]{
                1L, 2L, 3L, -4L, 5L, random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()
            };
            object.longWrapperArray = new Long[]{
                6L, 7L, 8L, -9L, 0L, random.nextLong(), random.nextLong(), random.nextLong(), random.nextLong()
            };
            object.floatArray = new float[]{
                1F, 2F, 3F, -4F, 5F, random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat()
            };
            object.floatWrapperArray = new Float[]{
                6F, 7F, 8F, -9F, 0F, random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat()
            };
            object.doubleArray = new double[]{
                1D, 2D, 3D, -4D, 5D, random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble()
            };
            object.doubleWrapperArray = new Double[]{
                6D, 7D, 8D, -9D, 0D, random.nextDouble(), random.nextDouble(), random.nextDouble(), random.nextDouble()
            };
            object.emptyRecordArray = new DummyRecord[0];
            object.emptyScalarArray = new int[0];
            object.stringArray = new String[10];
            for (int i = 0; i < object.stringArray.length; i++) {
                object.stringArray[i] = Long.toString(random.nextLong());
            }
            object.recordArray = new DummyRecord[10];
            for (int i = 0; i < object.recordArray.length; i++) {
                object.recordArray[i] = new DummyRecord(Double.toString(random.nextDouble()), random.nextInt());
            }
            object.recordList = new ArrayList<>();
            for (int i = 0; i <= random.nextInt(10); i++) {
                object.recordList.add(new DummyRecord(Double.toString(random.nextDouble()), random.nextInt()));
            }
            object.recordArrayList = new ArrayList<>();
            for (int i = 0; i <= random.nextInt(10); i++) {
                object.recordArrayList.add(new DummyRecord(Double.toString(random.nextDouble()), random.nextInt()));
            }
            object.recordLinkedList = new LinkedList<>();
            for (int i = 0; i <= random.nextInt(10); i++) {
                object.recordLinkedList.add(new DummyRecord(Double.toString(random.nextDouble()), random.nextInt()));
            }
            return object;
        }

        private byte[]                  byteArray;
        private short[]                 shortArray;
        private char[]                  charArray;
        private int[]                   intArray;
        private long[]                  longArray;
        private float[]                 floatArray;
        private double[]                doubleArray;
        private DummyRecord[]           emptyRecordArray;
        private int[]                   emptyScalarArray;
        private String[]                stringArray;
        private Byte[]                  byteWrapperArray;
        private Short[]                 shortWrapperArray;
        private Character[]             charWrapperArray;
        private Integer[]               intWrapperArray;
        private Long[]                  longWrapperArray;
        private Float[]                 floatWrapperArray;
        private Double[]                doubleWrapperArray;
        private DummyRecord[]           recordArray;
        private List<DummyRecord>       recordList;
        private ArrayList<DummyRecord>  recordArrayList;
        private LinkedList<DummyRecord> recordLinkedList;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DummyRecord {
        private String name;
        private int    value;
    }

    @Parameterized.Parameters(name = "{4}")
    public static Object[][] getParameters() {
        return getParameters(ArraysObject.class, ArraysObject.dummy(), ArraysObject.class);
    }

    @Test
    public void testArrayList() {
        assertThat(actual.recordArrayList, is(equalTo(expected.recordArrayList)));
    }

    @Test
    public void testByteArray() {
        assertThat(actual.byteArray, is(equalTo(expected.byteArray)));
    }

    @Test
    public void testByteWrapper() {
        assertThat(actual.byteWrapperArray, is(equalTo(expected.byteWrapperArray)));
    }

    @Test
    public void testCharacterArray() {
        assertThat(actual.charArray, is(equalTo(expected.charArray)));
    }

    @Test
    public void testCharacterWrapper() {
        assertThat(actual.charWrapperArray, is(equalTo(expected.charWrapperArray)));
    }

    @Test
    public void testDoubleArray() {
        assertThat(actual.doubleArray, is(equalTo(expected.doubleArray)));
    }

    @Test
    public void testDoubleWrapper() {
        assertThat(actual.doubleWrapperArray, is(equalTo(expected.doubleWrapperArray)));
    }

    @Test
    public void testEmptyRecordArray() {
        assertThat(actual.emptyRecordArray, is(equalTo(expected.emptyRecordArray)));
    }

    @Test
    public void testEmptyScalarArray() {
        assertThat(actual.emptyScalarArray, is(equalTo(expected.emptyScalarArray)));
    }

    @Test
    public void testFloatArray() {
        assertThat(actual.floatArray, is(equalTo(expected.floatArray)));
    }

    @Test
    public void testFloatWrapper() {
        assertThat(actual.floatWrapperArray, is(equalTo(expected.floatWrapperArray)));
    }

    @Test
    public void testIntArray() {
        assertThat(actual.intArray, is(equalTo(expected.intArray)));
    }

    @Test
    public void testIntWrapper() {
        assertThat(actual.intWrapperArray, is(equalTo(expected.intWrapperArray)));
    }

    @Test
    public void testLinkedList() {
        assertThat(actual.recordLinkedList, is(equalTo(expected.recordLinkedList)));
    }

    @Test
    public void testList() {
        assertThat(actual.recordList, is(equalTo(expected.recordList)));
    }

    @Test
    public void testLongArray() {
        assertThat(actual.longArray, is(equalTo(expected.longArray)));
    }

    @Test
    public void testLongWrapper() {
        assertThat(actual.longWrapperArray, is(equalTo(expected.longWrapperArray)));
    }

    @Test
    public void testRecordArray() {
        assertThat(actual.recordArray, is(equalTo(expected.recordArray)));
    }

    @Test
    public void testShortArray() {
        assertThat(actual.shortArray, is(equalTo(expected.shortArray)));
    }

    @Test
    public void testShortWrapper() {
        assertThat(actual.shortWrapperArray, is(equalTo(expected.shortWrapperArray)));
    }

    @Test
    public void testStringArray() {
        assertThat(actual.stringArray, is(equalTo(expected.stringArray)));
    }
}
