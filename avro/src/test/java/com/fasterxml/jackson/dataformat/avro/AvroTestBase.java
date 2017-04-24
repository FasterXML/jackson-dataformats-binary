package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import junit.framework.TestCase;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class AvroTestBase extends TestCase
{
    /*
    /**********************************************************
    /* Test schemas
    /**********************************************************
     */

    protected final String STRING_ARRAY_SCHEMA_JSON = "{\n"
            +"\"name\": \"StringArray\",\n"
            +"\"type\": \"array\",\n"
            +"\"items\": \"string\"\n}";

    protected final String STRING_MAP_SCHEMA_JSON = "{\n"
            +"\"name\": \"StringMap\",\n"
            +"\"type\": \"map\",\n"
            +"\"values\": \"string\"\n}";

    protected final String EMPLOYEE_SCHEMA_JSON = "{\n"
            +"\"type\": \"record\",\n"
            +"\"name\": \"Employee\",\n"
            +"\"fields\": [\n"
            +" {\"name\": \"name\", \"type\": \"string\"},\n"
            +" {\"name\": \"age\", \"type\": \"int\"},\n"
            +" {\"name\": \"emails\", \"type\": {\"type\": \"array\", \"items\": \"string\"}},\n"
            +" {\"name\": \"boss\", \"type\": [\"Employee\",\"null\"]}\n"
            +"]}";

    protected final String EMPLOYEE_ARRAY_SCHEMA_JSON = aposToQuotes(
            "{"
            +"'name': 'EmployeeArray',\n"
            +"'type': 'array',\n"
            +"'items': {\n"
            +"  'type': 'record',\n"
            +"  'name': 'Employee',\n"
            +"   'fields': [\n"
            +"    {'name': 'name', 'type': 'string'},\n"
            +"    {'name': 'age', 'type': 'int'},\n"
            +"    {'name': 'emails', 'type': {'type': 'array', 'items': 'string'}},\n"
            +"     {'name': 'boss', 'type': ['Employee','null']}\n"
            +"   ]}\n"
            +"}\n");

    protected final String POINT_LONG_SCHEMA_JSON = "{\n"
            +"\"type\": \"record\",\n"
            +"\"name\": \"Point\",\n"
            +"\"fields\": [\n"
            +" {\"name\": \"x\", \"type\": \"long\"},\n"
            +" {\"name\": \"y\", \"type\": \"long\"}\n"
            +"]}";

    protected final String POINT_DOUBLE_SCHEMA_JSON = "{\n"
            +"\"type\": \"record\",\n"
            +"\"name\": \"Point\",\n"
            +"\"fields\": [\n"
            +" {\"name\": \"x\", \"type\": \"double\"},\n"
            +" {\"name\": \"y\", \"type\": \"double\"}\n"
            +"]}";
    
    /*
    /**********************************************************
    /* Test classes
    /**********************************************************
     */

    public static class PointLong
    {
        public long x, y;

        protected PointLong() { }

        public PointLong(long x0, long y0) {
            x = x0;
            y = y0;
        }
    }

    public static class PointDouble
    {
        public double x, y;

        protected PointDouble() { }

        public PointDouble(long x0, long y0) {
            x = x0;
            y = y0;
        }
    }
    
    public static class Employee
    {
        public Employee() { }

        public Employee(String n,  int a, String[] e, Employee b) {
            name = n;
            age = a;
            emails = e;
            boss = b;
        }

        public static Employee construct() {
            return new Employee();
        }

        public String name;
        public int age;
        public String[] emails;
        public Employee boss;
    }

    @JsonPropertyOrder({"content", "images"})
    static class MediaItem
    {
        private MediaContent _content;
        private List<Image> _images;

        public MediaItem() { }

        public MediaItem(MediaContent c) {
            _content = c;
        }

        public void addPhoto(Image p) {
            if (_images == null) {
                _images = new ArrayList<Image>();
            }
            _images.add(p);
        }
        
        public List<Image> getImages() { return _images; }
        public void setImages(List<Image> p) { _images = p; }

        public MediaContent getContent() { return _content; }
        public void setContent(MediaContent c) { _content = c; }
    }

    @JsonPropertyOrder(alphabetic=true, value = {
            "uri","title","width","height","format","duration","size","bitrate","persons","player","copyright"})
    static class MediaContent
    {
        public enum Player { JAVA, FLASH;  }

        private Player _player;
        private String _uri;
        private String _title;
        private int _width;
        private int _height;
        private String _format;
        private long _duration;
        private long _size;
        private int _bitrate;
        private List<String> _persons;
        private String _copyright;

        public MediaContent() { }

        protected MediaContent(MediaContent src) {
            _player = src._player;
            _uri = src._uri;
            _title = src._title;
            _width = src._width;
            _height = src._height;
            _format = src._format;
            _duration = src._duration;
            _size = src._size;
            _bitrate = src._bitrate;
            _persons = src._persons;
            _copyright = src._copyright;
        }

        public void addPerson(String p) {
            if (_persons == null) {
                _persons = new ArrayList<String>();
            }
            _persons.add(p);
        }

        public Player getPlayer() { return _player; }
        public String getUri() { return _uri; }
        public String getTitle() { return _title; }
        public int getWidth() { return _width; }
        public int getHeight() { return _height; }
        public String getFormat() { return _format; }
        public long getDuration() { return _duration; }
        public long getSize() { return _size; }
        public int getBitrate() { return _bitrate; }
        public List<String> getPersons() { return _persons; }
        public String getCopyright() { return _copyright; }

        public void setPlayer(Player p) { _player = p; }
        public void setUri(String u) {  _uri = u; }
        public void setTitle(String t) {  _title = t; }
        public void setWidth(int w) {  _width = w; }
        public void setHeight(int h) {  _height = h; }
        public void setFormat(String f) {  _format = f;  }
        public void setDuration(long d) {  _duration = d; }
        public void setSize(long s) {  _size = s; }
        public void setBitrate(int b) {  _bitrate = b; }
        public void setPersons(List<String> p) {  _persons = p; }
        public void setCopyright(String c) {  _copyright = c; }
    }

    @JsonPropertyOrder({"uri","title","width","height","size"})
    static class Image
    {
        private String _uri;
        private String _title;
        private int _width;
        private int _height;
        private Size _size;

        public Image() {}
        public Image(String uri, String title, int w, int h, Size s)
        {
          _uri = uri;
          _title = title;
          _width = w;
          _height = h;
          _size = s;
        }

      public String getUri() { return _uri; }
      public String getTitle() { return _title; }
      public int getWidth() { return _width; }
      public int getHeight() { return _height; }
      public Size getSize() { return _size; }

      public void setUri(String u) { _uri = u; }
      public void setTitle(String t) { _title = t; }
      public void setWidth(int w) { _width = w; }
      public void setHeight(int h) { _height = h; }
      public void setSize(Size s) { _size = s; }
    }

    public enum Size { SMALL, LARGE; }

    /*
    /**********************************************************
    /* Recycling for commonly needed helper objects
    /**********************************************************
     */

    protected AvroSchema _employeeSchema;

    protected AvroMapper _sharedMapper;

    protected AvroTestBase() { }

    /*
    /**********************************************************
    /* Helper methods for subclasses
    /**********************************************************
     */

    protected AvroSchema getEmployeeSchema() throws IOException {
        if (_employeeSchema == null) {
            _employeeSchema = getMapper().schemaFrom(EMPLOYEE_SCHEMA_JSON);
        }
        return _employeeSchema;
    }

    protected AvroSchema getStringArraySchema() throws IOException {
        return getMapper().schemaFrom(STRING_ARRAY_SCHEMA_JSON);
    }

    protected AvroSchema getStringMapSchema() throws IOException {
        return getMapper().schemaFrom(STRING_MAP_SCHEMA_JSON);
    }

    protected AvroMapper getMapper() {
        if (_sharedMapper == null) {
            _sharedMapper = newMapper();
        }
        return _sharedMapper;
    }

    protected AvroMapper newMapper() {
        return new AvroMapper();
    }
    
    protected byte[] toAvro(Employee empl) throws IOException {
        return toAvro(empl, getMapper());
    }
    protected byte[] toAvro(Employee empl, ObjectMapper mapper) throws IOException {
        return mapper.writer(getEmployeeSchema()).writeValueAsBytes(empl);
    }

    protected void assertToken(JsonToken expToken, JsonToken actToken)
    {
        if (actToken != expToken) {
            fail("Expected token "+expToken+", current token "+actToken);
        }
    }

    protected void assertToken(JsonToken expToken, JsonParser jp)
    {
        assertToken(expToken, jp.getCurrentToken());
    }

    public static void verifyException(Throwable e, String... matches)
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

    protected static String quote(String str) {
        return "\""+str+"\"";
    }
    
    protected static String aposToQuotes(String json) {
        return json.replace("'", "\"");
    }

    /*
    /**********************************************************
    /* Text generation
    /**********************************************************
     */

    protected static String generateUnicodeString(int length) {
        return generateUnicodeString(length, new Random(length));
    }
    
    protected static String generateUnicodeString(int length, Random rnd)
    {
        StringBuilder sw = new StringBuilder(length+10);
        do {
            // First, add 7 ascii characters
            int num = 4 + (rnd.nextInt() & 7);
            while (--num >= 0) {
                sw.append((char) ('A' + num));
            }
            // Then a unicode char of 2, 3 or 4 bytes long
            switch (rnd.nextInt() % 3) {
            case 0:
                sw.append((char) (256 + rnd.nextInt() & 511));
                break;
            case 1:
                sw.append((char) (2048 + rnd.nextInt() & 4095));
                break;
            default:
                sw.append((char) (65536 + rnd.nextInt() & 0x3FFF));
                break;
            }
        } while (sw.length() < length);
        return sw.toString();
    }

    protected static String generateAsciiString(int length) {
        return generateAsciiString(length, new Random(length));
    }
    
    protected static String generateAsciiString(int length, Random rnd)
    {
        StringBuilder sw = new StringBuilder(length+10);
        do {
            // First, add 7 ascii characters
            int num = 4 + (rnd.nextInt() & 7);
            while (--num >= 0) {
                sw.append((char) ('A' + num));
            }
            // and space
            sw.append(' ');
        } while (sw.length() < length);
        return sw.toString();
    }
}
