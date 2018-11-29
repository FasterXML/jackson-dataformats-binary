package com.fasterxml.jackson.dataformat.protobuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufField;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufMessage;

import junit.framework.TestCase;

public abstract class ProtobufTestBase extends TestCase
{
    /*
    /**********************************************************
    /* Basic protoc definitions
    /**********************************************************
     */

    enum Corpus {
        UNIVERSAL,
        WEB;
    }

    static class SearchRequest {
        public String query;
        public int page_number, result_per_page;
        public Corpus corpus;
    }

    final protected static String PROTOC_SEARCH_REQUEST = "message SearchRequest {\n"
            +" required string query = 1;\n"
            +" optional int32 page_number = 2;\n"
            +" optional int32 result_per_page = 3;\n"
            +" enum Corpus {\n"
            +"   UNIVERSAL = 10;\n"
            +"   WEB = 20;\n"
            +" }\n"
            +" optional Corpus corpus = 4 [default = UNIVERSAL];\n"
            +"}\n"
    ;

    final protected static String PROTOC_POINT =
            "message Point {\n"
            +" required int32 x = 1;\n"
            +" required sint32 y = 2;\n"
            +"}\n";

    final protected static String PROTOC_POINT3 =
            "message Point {\n"
            +" required int32 x = 1;\n"
            +" required sint32 y = 2;\n"
            +" optional sint32 z = 3;\n"
            +"}\n";
    
    final protected static String PROTOC_POINT_L =
            "message Point {\n"
            +" required int64 x = 1;\n"
            +" required sint64 y = 2;\n"
            +"}\n";

    final protected static String PROTOC_POINT_FL =
            "message Point {\n"
            +" required fixed64 x = 1;\n"
            +" required fixed64 y = 2;\n"
            +"}\n";

    final protected static String PROTOC_POINT_D =
            "message Point {\n"
            +" required double x = 1;\n"
            +" required float y = 2;\n"
            +"}\n";
    
    final protected static String PROTOC_BOX =
            "message Box {\n"
            +" required Point topLeft = 3;\n"
            +" required Point bottomRight = 5;\n"
            +"}\n"
            +PROTOC_POINT;
    ;

    final protected static String PROTOC_NODE =
            "message Node {\n"
            +" required int32 id = 1;\n"
            +" optional Node left = 2;\n"
            +" optional Node right = 3;\n"
            +"}\n"
    ;

    final protected static String PROTOC_NAME =
            "message Name {\n"
            +" optional string first = 2;\n"
            +" required string last = 7;\n"
            +"}\n"
    ;

    final protected static String PROTOC_OPTIONAL_VALUE =
            "message Value {\n"
            +" required bool present = 1;\n"
            +" optional string value = 3;\n"
            +"}\n"
    ;
    
    // protoc definition from 'jvm-serializers' project:
    final protected static String PROTOC_MEDIA_ITEM =
"package serializers.protobuf.media;\n"+
"option java_package = \"serializers.protobuf.media\";\n"+
"option java_outer_classname = \"MediaContentHolder\";\n"+
"option optimize_for = SPEED;\n"+
"\n"+
"message MediaItem {\n"+
"  required Media media = 1;\n"+
"  repeated Image images = 2;\n"+
"}\n"+
"message Image {\n"+
"  required string uri = 1;\n"+
"  optional string title = 4;\n"+
"  required int32 width = 5;\n"+
"  required int32 height = 6;\n"+
"  enum Size {\n"+
"    SMALL = 0;\n"+
"    LARGE = 1;\n"+
"  }\n"+
"  required Size size = 7;\n"+
"}\n"+
"message Media {\n"+
"  required string uri = 1;\n"+
"  optional string title = 11;\n"+
"  required int32 width = 12;\n"+
"  required int32 height = 13;\n"+
"  required string format = 14;\n"+
"  required int64 duration = 15;\n"+
"  required int64 size = 16;\n"+
"  optional int32 bitrate = 17;\n"+
"  repeated string persons = 18;\n"+
"  enum Player {\n"+
"    JAVA = 4;\n"+
"    FLASH = 5;\n"+
"}\n"+
"required Player player = 20;\n"+
"optional string copyright = 21;\n"+
"}\n"
;
    
    /*
    /**********************************************************
    /* POJO classes to use with protoc definitions
    /**********************************************************
     */

    static class Point {
        public int x;
        public int y;

        protected Point() { }
        
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "[x="+x+",y="+y+"]";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o.getClass() != getClass()) return false;
            Point other = (Point) o;
            return (other.x == x) && (other.y == y);
        }
    }

    static class Point3 {
        public int x, y, z;

        protected Point3() { }
        
        public Point3(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public String toString() {
            return "[x="+x+",y="+y+",z="+z+"]";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o.getClass() != getClass()) return false;
            Point3 other = (Point3) o;
            return (other.x == x) && (other.y == y) && (other.z == z);
        }
    }
    
    static class PointL {
        public long x;
        public long y;

        protected PointL() { }
        
        public PointL(long x, long y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "[x="+x+",y="+y+"]";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o.getClass() != getClass()) return false;
            PointL other = (PointL) o;
            return (other.x == x) && (other.y == y);
        }
    }

    static class OptionalValue {
        public boolean present;
        public String value;

        protected OptionalValue() { }
        public OptionalValue(boolean p, String v) {
            present = p;
            value = v;
        }

        @Override
        public String toString() {
            return String.format("[present=%s, value=%s]", present, value);
        }
        
        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o.getClass() != getClass()) return false;
            OptionalValue other = (OptionalValue) o;
            if (other.present != present) {
                return false;
            }
            if (value == other.value) return true;
            return (value != null) && value.equals(other.value);
        }
    }

    static class PointD {
        public double x;
        public double y;

        protected PointD() { }
        
        public PointD(double x, double y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "[x="+x+",y="+y+"]";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o.getClass() != getClass()) return false;
            PointD other = (PointD) o;
            return (other.x == x) && (other.y == y);
        }
    }
    
    static class Box {
        public Point topLeft, bottomRight;

        public Box() { }
        public Box(Point tl, Point br) {
            topLeft = tl;
            bottomRight = br;
        }

        public Box(int x1, int y1, int x2, int y2) {
            this(new Point(x1, y1), new Point(x2, y2));
        }

        @Override
        public String toString() {
            return "[topLeft="+topLeft+",bottomRight="+bottomRight+"]";
        }
    }

    static class Name {
        public String first, last;

        public Name() { }
        public Name(String f, String l) {
            first = f;
            last = l;
        }

        @Override
        public String toString() {
            return "[first="+first+", last="+last+"]";
        }
    }

    // // // POJOs for "JVM-serializers" case
    
    protected static class  MediaItem
    {
         public Media media;
         public List<Image> images;

         public MediaItem() { }
         
         public MediaItem addPhoto(Image i) {
             if (images == null) {
                 images = new ArrayList<Image>();
             }
             images.add(i);
             return this;
         }

         public static MediaItem buildItem()
         {
             Media content = new Media();
             content.player = Player.JAVA;
             content.uri = "http://javaone.com/keynote.mpg";
             content.title = "Javaone Keynote";
             content.width = 640;
             content.height = 480;
             content.format = "video/mpeg4";
             content.duration = 18000000L;
             content.size = 58982400L;
             content.bitrate = 262144;
             content.copyright = "None";
             content.addPerson("Bill Gates");
             content.addPerson("Steve Jobs");

             MediaItem item = new MediaItem();
             item.media = content;

             item.addPhoto(new Image("http://javaone.com/keynote_large.jpg", "Javaone Keynote", 1024, 768, Size.LARGE));
             item.addPhoto(new Image("http://javaone.com/keynote_small.jpg", "Javaone Keynote", 320, 240, Size.SMALL));

             return item;
         }

         @Override
         public String toString() {
             return "{MediaItem: media="+media+", images="+images+"}";
         }
         
         @Override
         public boolean equals(Object o) {
             if (o == this) return true;                     
             if (o == null) return false;
             if (o.getClass() != getClass()) return false;

             MediaItem other = (MediaItem) o;

             return _equals(media, other.media) && _equals(images, other.images);
         }
    }

    enum Size { SMALL, LARGE };

    static class Image
    {
        public Image() { }
        public Image(String uri, String title, int w, int h, Size s) {
            this.uri = uri;
            this.title = title;
            width = w;
            height = h;
            size = s;
        }

        public String uri;
        public String title;
        public int width, height;
        public Size size;    

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;                     
            if (o == null) return false;
            if (o.getClass() != getClass()) return false;

            Image other = (Image) o;

            return _equals(uri, other.uri)
                    && _equals(title, other.title)
                    && _equals(size, other.size)
                    && (width == other.width)
                    && (height == other.height)
                    ;
        }
    } 
    
    enum Player { JAVA, FLASH; }

    static class Media {

        public String uri;
        public String title;        // Can be unset.
        public int width;
        public int height;
        public String format;
        public long duration;
        public long size;
        public int bitrate;         // Can be unset.

        public List<String> persons;
        
        public Player player;

        public String copyright;    // Can be unset.    

        public Media addPerson(String p) {
            if (persons == null) {
                persons = new ArrayList<String>();
            }
            persons.add(p);
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;                     
            if (o == null) return false;
            if (o.getClass() != getClass()) return false;

            Media other = (Media) o;

            return _equals(uri, other.uri)
                    && _equals(title, other.title)
                    && (width == other.width)
                    && (height == other.height)
                    && _equals(format, other.format)
                    && (duration == other.duration)
                    && (size == other.size)
                    && _equals(persons, other.persons)
                    && _equals(player, other.player)
                    && _equals(copyright, other.copyright)
                    ;
        }
    }

    /*
    /**********************************************************
    /* Factory methods
    /**********************************************************
     */
    
    protected ProtobufMapper newObjectMapper() {
        return new ProtobufMapper();
    }

    /*
    /**********************************************************
    /* Additional assertion methods
    /**********************************************************
     */

    protected void _verifyMessageFieldLinking(ProtobufMessage msg)
    {
        ProtobufField prev = null;
        for (ProtobufField curr : msg.fields()) {
            if (prev != null) {
                if (prev.next != curr) {
                    fail("Linking broken for type '"+msg.getName()+", field '"+curr.name+"'; points to "+prev.next);
                }
            }
            prev = curr;
        }
        // also, verify that the last field not linked
        if (prev.next != null) {
            fail("Linking broken for type '"+msg.getName()+", last field '"+prev.name
                    +"' should be null, points to "+prev.next);
        }
    }
    
    protected void assertToken(JsonToken expToken, JsonToken actToken)
    {
        if (actToken != expToken) {
            fail("Expected token "+expToken+", current token "+actToken);
        }
    }

    protected void assertToken(JsonToken expToken, JsonParser p)
    {
        assertToken(expToken, p.getCurrentToken());
    }

    protected void assertType(Object ob, Class<?> expType)
    {
        if (ob == null) {
            fail("Expected an object of type "+expType.getName()+", got null");
        }
        Class<?> cls = ob.getClass();
        if (!expType.isAssignableFrom(cls)) {
            fail("Expected type "+expType.getName()+", got "+cls.getName());
        }
    }

    protected void verifyException(Throwable e, String... matches)
    {
        String msg = e.getMessage();
        String lmsg = (msg == null) ? "" : msg.toLowerCase();
        for (String match : matches) {
            String lmatch = match.toLowerCase();
            if (lmsg.indexOf(lmatch) >= 0) {
                return;
            }
        }
        fail("Expected an exception with one of substrings ("+Arrays.asList(matches)+"): got one with message \""+msg+"\"");
    }

    static <T> boolean _equals(T ob1, T ob2) {
        if (ob1 == null) {
            return (ob2 == null);
        }
        return ob1.equals(ob2);
    }
}
