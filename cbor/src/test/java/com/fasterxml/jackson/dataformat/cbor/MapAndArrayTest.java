package com.fasterxml.jackson.dataformat.cbor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MapAndArrayTest extends CBORTestBase
{
    /**
     * Test for verifying complex Array and Map generation with limited and unlimited size
     */

    public void testCborBasicMap() throws IOException {
    /*
       {_"Fun": true, "Amt": -2}
       0xbf6346756ef563416d7421ff
       BF           -- Start indefinite-length map
          63        -- First key, UTF-8 string length 3
             46756e --   "Fun"
          F5        -- First value, true
          63        -- Second key, UTF-8 string length 3
             416d74 --   "Amt"
          21        -- -2
          FF        -- "break"
     */
        ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
        CBORFactory factory = new CBORFactory();
        CBORGenerator gen = factory.createGenerator(payloadOut);

        gen.writeStartObject();
        gen.writeBooleanField("Fun", true);
        gen.writeNumberField("Amt", -2);
        gen.writeEndObject();
        gen.close();

        byte [] bytes = payloadOut.toByteArray();
        String hexData = asHex(bytes);

        assertTrue(hexData.equalsIgnoreCase("bf6346756ef563416d7421ff"));
    }
    
    public void testCborUnsizedMap() throws IOException {
    /* {_"Fun": true, 1504: -33, 1505: false, 13171233041: 22}

       0xbf6346756ef51905e038201905e1f41b000000031111111116ff
       BF           -- Start indefinite-length map
          63        -- First key, UTF-8 string length 3
             46756e --   "Fun"
          F5        -- First value, true
          19        -- Second key, Unsigned integer (two-byte uint16_t)
             05E0 	--   1504
          38        -- Second value, Negative integer -1-n (one-byte uint8_t)
          20		-- 33 as unsigned
          19		-- Third key, Unsigned integer (two-byte uint16_t)
             05E1 --   1505
          F4        -- Third value, false
          1B		-- Fourth key, Unsigned integer (eight-byte uint64_t)
             0000000311111111 --   13171233041
          16        -- Fourth value, 22
          FF        -- "break"
     */

        ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
        CBORFactory factory = new CBORFactory();
        CBORGenerator gen = factory.createGenerator(payloadOut);

        gen.writeStartObject();
        gen.writeFieldName("Fun");
        gen.writeBoolean(true);
        gen.writeFieldId(1504);
        gen.writeNumber(-33);
        gen.writeFieldId(1505);
        gen.writeBoolean(false);
        gen.writeFieldId(13171233041L);
        gen.writeNumber(22);
        gen.close();

        String hexData = asHex(payloadOut.toByteArray());

        assertTrue(hexData.equalsIgnoreCase("bf6346756ef51905e038201905e1f41b000000031111111116ff"));
    }



    public void testCborSizedMap() throws IOException {
    /*
        {1504: -33, 1505: false, 1506: "Fun", "Amt": [2, 3], 1507: false}

        0xA51905e038201905e1f41905E26346756e63416d748202031905E3F4
        A5           -- Start definite-length map of 4 pairs
          19        -- First  key, Unsigned integer (two-byte uint16_t)
             05E0 	--   1504
          38        -- First  value, Negative integer -1-n (one-byte uint8_t)
          20		-- 33 as unsigned
          19		-- Second key, Unsigned integer (two-byte uint16_t)
             05E1 --   1505
          F4        -- Second value, false
          19		-- Third key, Unsigned integer (two-byte uint16_t)
             05E2 --   1506
          63        -- Third value, UTF-8 string length 3
             46756e -- "Fun"
          63        -- Fourth key, UTF-8 string length 3
          416d74    -- "Amt"
             82        -- Fourth value, array of 2 elements
                0203		-- array elements: 2, 3
          19		-- Fifth key, Unsigned integer (two-byte uint16_t)
             05E3 --   1507
          F4        -- Fifth value, false
    */
        ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
        CBORFactory factory = new CBORFactory();
        CBORGenerator gen = factory.createGenerator(payloadOut);

        gen.writeStartObject(5);
        gen.writeFieldId(1504);
        gen.writeNumber(-33);
        gen.writeFieldId(1505);
        gen.writeBoolean(false);
        gen.writeFieldId(1506);
        gen.writeString("Fun");
        gen.writeFieldName("Amt");
        gen.writeStartArray(2);
        gen.writeNumber(2);
        gen.writeNumber(3);
        gen.writeEndArray();
        gen.writeFieldId(1507);
        gen.writeBoolean(false);
        gen.writeEndObject();
        gen.close();

        String hexData = asHex(payloadOut.toByteArray());
        assertTrue(hexData.equalsIgnoreCase("A51905e038201905e1f41905E26346756e63416d748202031905E3F4"));
    }

    public void testCborSizedMapWithParserTest() throws IOException {
    /*
       {_ 1504:-33, 1505:false, 1506:"Fun", 1507: [_"c", 3, false], 13171233041:false }

       0xBF1905e038201905e1f41905E26346756e63416d748202031905E3F4
       BF           -- Start undefinite-length map
          19        -- First  key, Unsigned integer (two-byte uint16_t)
             05E0 	--   1504
          38        -- First  value, Negative integer -1-n (one-byte uint8_t)
          20		-- 33 as unsigned
          19		-- Second key, Unsigned integer (two-byte uint16_t)
             05E1 --   1505
          F4        -- Second value, false
          19		-- Third key, Unsigned integer (two-byte uint16_t)
             05E2 --   1506
          63        -- Third value, UTF-8 string length 3
             46756e -- "Fun"
          63        -- Fourth key, UTF-8 string length 3
          416d74    -- "Amt"
             82        -- Fourth value, array of 2 elements
                0203		-- array elements: 2, 3
          1B		-- Fifth key, Unsigned integer (eight-byte uint64_t)
             0000000311111111 --   13171233041
          F4        -- Fifth value, false
     */
        ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
        CBORFactory factory = new CBORFactory();
        CBORGenerator gen = factory.createGenerator(payloadOut);

        gen.writeStartObject();
        gen.writeFieldId(1504);
        gen.writeNumber(-33);
        gen.writeFieldId(1505);
        gen.writeBoolean(false);
        gen.writeFieldId(1506);
        gen.writeString("Fun");
        gen.writeFieldId(1507);
        gen.writeStartArray();
        gen.writeString("c");
        gen.writeNumber(3);
        gen.writeBoolean(false);
        gen.writeEndArray();
        gen.writeFieldId(13171233041L);
        gen.writeBoolean(false);
        gen.writeEndObject();
        gen.close();

        final byte[] bytes = payloadOut.toByteArray();
        String hexData = asHex(bytes);
        assertTrue(hexData.equalsIgnoreCase("BF1905e038201905e1f41905E26346756e1905E39F616303F4FF1B0000000311111111F4FF"));

        /*
            Parser test for the first element
         */
        CBORParser parser = factory.createParser(bytes);
        parser.nextToken();
        parser.nextToken();
        assertTrue(parser.getCurrentName().equals("1504"));
        parser.close();
    }

    public void testCborUnsizedMapWithArrayAsKey() throws IOException {
	/*
        {_ "a": 1, "b": [_ 2, 3]}

        0xbf61610161629f0203ffff
 	*/
        ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
        CBORFactory factory = new CBORFactory();
        CBORGenerator gen = factory.createGenerator(payloadOut);

        gen.writeStartObject();
        gen.writeFieldName("a");
        gen.writeNumber(1);
        gen.writeFieldName("b");
        gen.writeStartArray();
        gen.writeNumber(2);
        gen.writeNumber(3);
        gen.writeEndArray();
        gen.close();

        String hexData = asHex(payloadOut.toByteArray());
        assertTrue(hexData.equalsIgnoreCase("bf61610161629f0203ffff"));
    }

    public void testCborMultilevelMapWithMultilevelArrays() throws IOException {
	/*
        { "a": 1, "b": [_ 2, 3], 1501: ["Fun", 44, [_ 45, 46, [ 47, 48]], { "key": {_"complex": 50}, 51: "52"}, 53], 1502: {_54: "value", 55: {56:61, 57:62}}}

        0xa0...
 	*/
        ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
        CBORFactory factory = new CBORFactory();
        CBORGenerator gen = factory.createGenerator(payloadOut);

        gen.writeStartObject(4);
        gen.writeFieldName("a");
        gen.writeNumber(1);

        gen.writeFieldName("b");
        gen.writeStartArray();
        gen.writeNumber(2);
        gen.writeNumber(3);
        gen.writeEndArray();
        gen.writeFieldId(1501);
        gen.writeStartArray(5);
        gen.writeString("Fun");
        gen.writeNumber(44);
        gen.writeStartArray();
        gen.writeNumber(45);
        gen.writeNumber(46);
        gen.writeStartArray(2);
        gen.writeNumber(47);
        gen.writeNumber(48);
        gen.writeEndArray();
        gen.writeEndArray();
        gen.writeStartObject(2);
        gen.writeFieldName("key");
        gen.writeStartObject();
        gen.writeFieldName("complex");
        gen.writeNumber(50);
        gen.writeEndObject();
        gen.writeFieldId(51);
        gen.writeString("52");
        gen.writeEndObject();   //
        gen.writeNumber(53);
        gen.writeEndArray();
        gen.writeFieldId(1502);
        gen.writeStartObject();
        gen.writeFieldId(54);
        gen.writeString("value");
        gen.writeFieldId(55);
        gen.writeStartObject(2);
        gen.writeFieldId(56);
        gen.writeNumber(61);
        gen.writeFieldId(57);
        gen.writeNumber(62);
        gen.writeEndObject();
        gen.writeEndObject();
        gen.writeEndObject();
        gen.close();

        String hexData = asHex(payloadOut.toByteArray());
        assertTrue(hexData.equalsIgnoreCase("a461610161629f0203ff1905dd856346756e182c9f182d182e82182f1830ffa2636b6579bf67636f6d706c65781832ff183362353218351905debf18366576616c75651837a21838183d1839183eff"));
    }

    public void testCborUnsizedMapWithAllInside() throws IOException {
    /*
       {_ 1504: { 2504:-33}, 1505:false, 1506:"Fun", 1507: [_"c", 3, false], 13171233041:false }

       0xBF1905e0A11909C838201905e1f41905E26346756e1905E39F616303F4FF1B0000000311111111F4FF
    */
        ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
        CBORFactory factory = new CBORFactory();
        CBORGenerator gen = factory.createGenerator(payloadOut);

        gen.writeStartObject();
        gen.writeFieldId(1504);
        gen.writeStartObject(1);
        gen.writeFieldId(2504);
        gen.writeNumber(-33);
        gen.writeEndObject();
        gen.writeFieldId(1505);
        gen.writeBoolean(false);
        gen.writeFieldId(1506);
        gen.writeString("Fun");
        gen.writeFieldId(1507);
        gen.writeStartArray();
        gen.writeString("c");
        gen.writeNumber(3);
        gen.writeBoolean(false);
        gen.writeEndArray();
        gen.writeFieldId(13171233041L);
        gen.writeBoolean(false);
        gen.writeEndObject();
        gen.close();

        String hexData = asHex(payloadOut.toByteArray());
        assertTrue(hexData.equalsIgnoreCase("BF1905e0A11909C838201905e1f41905E26346756e1905E39F616303F4FF1B0000000311111111F4FF"));
    }

    public void testCborArraysInArray() throws IOException {
	/*
        [_ 1, [2, 3], [_ 4, 5]]

        0x9f018202039f0405ffff
 	*/
        ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
        CBORFactory factory = new CBORFactory();
        CBORGenerator gen = factory.createGenerator(payloadOut);

        gen.writeStartArray();
        gen.writeNumber(1);
        gen.writeStartArray(2);
        gen.writeNumber(2);
        gen.writeNumber(3);
        gen.writeEndArray();
        gen.writeStartArray();
        gen.writeNumber(4);
        gen.writeNumber(5);
        gen.writeEndArray();
        gen.writeEndArray();
        gen.close();

        String hexData = asHex(payloadOut.toByteArray());
        assertTrue(hexData.equalsIgnoreCase("9f018202039f0405ffff"));
    }

    public void testCborArraysInUnsizedArray() throws IOException {
	/*
        [_ 1, [2, 3], [_ 4, 5], [6, 7, [_ 8, 8, [1, 1]]], [9, 9], [_ 0, 1] ]
        0x9f018202039f0405ff8306079f080808820101ff8209099f0001ffff
 	*/
        ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
        CBORFactory factory = new CBORFactory();
        CBORGenerator gen = factory.createGenerator(payloadOut);
        gen.writeStartArray();
        gen.writeNumber(1);
        gen.writeStartArray(2);
        gen.writeNumber(2);
        gen.writeNumber(3);
        gen.writeEndArray();
        gen.writeStartArray();
        gen.writeNumber(4);
        gen.writeNumber(5);
        gen.writeEndArray();
        gen.writeStartArray(3);
        gen.writeNumber(6);
        gen.writeNumber(7);
        gen.writeStartArray();
        gen.writeNumber(8);
        gen.writeNumber(8);
        gen.writeNumber(8);
        gen.writeStartArray(2);
        gen.writeNumber(1);
        gen.writeNumber(1);
        gen.writeEndArray();
        gen.writeEndArray();
        gen.writeEndArray();
        gen.writeStartArray(2);
        gen.writeNumber(9);
        gen.writeNumber(9);
        gen.writeEndArray();
        gen.writeStartArray();
        gen.writeNumber(0);
        gen.writeNumber(1);
        gen.writeEndArray();
        gen.writeEndArray();
        gen.close();

        String hexData = asHex(payloadOut.toByteArray());
        assertTrue(hexData.equalsIgnoreCase("9f018202039f0405ff8306079f080808820101ff8209099f0001ffff"));
    }

    public void testCborArraysInSizedArray() throws IOException {
	/*
   	    [1, [_2, 3, 4], [_ 4, [5, [_6, 6, 6]]], [7, 8, [_ 9, 10]]]

   	    0x84019f020304ff9f0482059f060606ffff8307089f090aff
 	*/
        ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
        CBORFactory factory = new CBORFactory();
        CBORGenerator gen = factory.createGenerator(payloadOut);

        gen.writeStartArray(4);//      [
        gen.writeNumber(1);    //      [1
        gen.writeStartArray();  //      [1,[_
        gen.writeNumber(2);    //      [1,[_2,
        gen.writeNumber(3);    //      [1,[_2,3,
        gen.writeNumber(4);    //      [1,[_2,3,4,
        gen.writeEndArray();    //      [1,[_2,3,4,_]
        gen.writeStartArray();  //      [1,[_2,3,4,_][_
        gen.writeNumber(4);    //      [1,[_2,3,4,_][_4,
        gen.writeStartArray(2);//      [1,[_2,3,4,_][_4,[
        gen.writeNumber(5);    //      [1,[_2,3,4,_][_4,[5,
        gen.writeStartArray();  //      [1,[_2,3,4,_][_4,[5,[
        gen.writeNumber(6);    //      [1,[_2,3,4,_][_4,[5,[_6
        gen.writeNumber(6);    //      [1,[_2,3,4,_][_4,[5,[_6,6
        gen.writeNumber(6);    //      [1,[_2,3,4,_][_4,[5,[_6,6,6
        gen.writeEndArray();    //      [1,[_2,3,4,_][_4,[5,[_6,6,6]
        gen.writeEndArray();    //      [1,[_2,3,4,_][_4,[5,[_6,6,6]]
        gen.writeEndArray();    //      [1,[_2,3,4,_][_4,[5,[_6,6,6]]]
        gen.writeStartArray(3);//      [1,[_2,3,4,_][_4,[5,[_6,6,6]]],[
        gen.writeNumber(7);    //      [1,[_2,3,4,_][_4,[5,[_6,6,6]]],[7
        gen.writeNumber(8);    //      [1,[_2,3,4,_][_4,[5,[_6,6,6]]],[7,8
        gen.writeStartArray();  //      [1,[_2,3,4,_][_4,[5,[_6,6,6]]],[7,8,[_
        gen.writeNumber(9);    //      [1,[_2,3,4,_][_4,[5,[_6,6,6]]],[7,8,[_9,
        gen.writeNumber(10);   //      [1,[_2,3,4,_][_4,[5,[_6,6,6]]],[7,8,[_9,10
        gen.writeEndArray();    //      [1,[_2,3,4,_][_4,[5,[_6,6,6]]],[7,8,[_9,10]
        gen.writeEndArray();    //      [1,[_2,3,4,_][_4,[5,[_6,6,6]]],[7,8,[_9,10]]
        gen.writeEndArray();    //      [1,[_2,3,4,_][_4,[5,[_6,6,6]]],[7,8,[_9,10]]]
        gen.close();

        String hexData = asHex(payloadOut.toByteArray());
        assertTrue(hexData.equalsIgnoreCase("84019f020304ff9f0482059f060606ffff8307089f090aff"));
    }

    public void testCborSizedArray() throws IOException {

	/*  [ 33, [256, 255, ..., 0], 34 ]
        0x8318219901000102030405...18FE18FF1901001822
        258 elements array with an array of 256 inside uint16 size marker:
        83         	-- Start finite-length array of 3
            01		-- First byte (MSB) of size marker
            02		-- Second byte (LSB) of size marker (total size of 0x102)
            18		-- Unsigned integer (one-byte uint8_t follows)
                21	-- 33 decimal value
            99		-- Start finite-length array of 256 with two bytes of size marker
                0100	-- Two bytes (MSB) of size marker (total size of 0x100)
                01	-- 01 value
                02	-- 02 value
                ...
                18	-- Unsigned integer (one-byte uint8_t follows)
                    FF -- 255 value
                19	-- Unsigned integer (two-byte uint16_t follows)
                0100 -- 256 value
            18		-- Unsigned integer (one-byte uint8_t follows)
                22	-- 34 decimal value (last value)
    */
        ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
        CBORFactory factory = new CBORFactory();
        CBORGenerator gen = factory.createGenerator(payloadOut);
        int size_finite_array = 258;

        gen.writeStartArray(3);
        gen.writeNumber(33);
        gen.writeStartArray(size_finite_array - 2);
        for (int i = 0; i < size_finite_array - 2; i++) {
            gen.writeNumber(i + 1);
        }
        gen.writeEndArray();
        gen.writeNumber(34);
        gen.writeEndArray();
        gen.close();

        String hexData = asHex(payloadOut.toByteArray());
        assertTrue(hexData.equalsIgnoreCase("8318219901000102030405060708090A0B0C0D0E0F101112131415161718181819181A181B181C181D181E181F1820182118221823182418251826182718281829182A182B182C182D182E182F1830183118321833183418351836183718381839183A183B183C183D183E183F1840184118421843184418451846184718481849184A184B184C184D184E184F1850185118521853185418551856185718581859185A185B185C185D185E185F1860186118621863186418651866186718681869186A186B186C186D186E186F1870187118721873187418751876187718781879187A187B187C187D187E187F1880188118821883188418851886188718881889188A188B188C188D188E188F1890189118921893189418951896189718981899189A189B189C189D189E189F18A018A118A218A318A418A518A618A718A818A918AA18AB18AC18AD18AE18AF18B018B118B218B318B418B518B618B718B818B918BA18BB18BC18BD18BE18BF18C018C118C218C318C418C518C618C718C818C918CA18CB18CC18CD18CE18CF18D018D118D218D318D418D518D618D718D818D918DA18DB18DC18DD18DE18DF18E018E118E218E318E418E518E618E718E818E918EA18EB18EC18ED18EE18EF18F018F118F218F318F418F518F618F718F818F918FA18FB18FC18FD18FE18FF1901001822"));
    }

    public void testCborSizedArrayWithMap() throws IOException {
	/*
        ["a", {_ "b": "c", "d": "e", }]
        0x826161bf6162616361646165ff
 	*/
        ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
        CBORFactory factory = new CBORFactory();
        CBORGenerator gen = factory.createGenerator(payloadOut);

        gen.writeStartArray(2);
        gen.writeString("a");
        gen.writeStartObject();
        gen.writeFieldName("b");
        gen.writeString("c");
        gen.writeFieldName("d");
        gen.writeString("e");
        gen.writeEndObject();
        gen.writeEndArray();
        gen.close();

        String hexData = asHex(payloadOut.toByteArray());
        assertTrue(hexData.equalsIgnoreCase("826161bf6162616361646165ff"));
    }

    private String asHex(byte[] data) {
        // Let's NOT rely on JDK converters as things get trickier with Java 9.
        // Brute force and ugly but will do
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (int i = 0, end = data.length; i < end; ++i) {
            sb.append(String.format("%02x", data[i] & 0xFF));
        }
        return sb.toString();
    }
}
