package com.fasterxml.jackson.dataformat.avro;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;

public abstract class AvroTestBase extends TestCase
{
    /*
    /**********************************************************
    /* Test schemas
    /**********************************************************
     */
    
    protected final String EMPLOYEE_SCHEMA_JSON = "{\n"
            +"\"type\": \"record\",\n"
            +"\"name\": \"Employee\",\n"
            +"\"fields\": [\n"
            +" {\"name\": \"name\", \"type\": \"string\"},\n"
            +" {\"name\": \"age\", \"type\": \"int\"},\n"
            +" {\"name\": \"emails\", \"type\": {\"type\": \"array\", \"items\": \"string\"}},\n"
            +" {\"name\": \"boss\", \"type\": [\"Employee\",\"null\"]}\n"
            +"]}";

    /*
    /**********************************************************
    /* Test classes
    /**********************************************************
     */
    
    protected static class Employee
    {
        public Employee() { }
        
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

    protected static String aposToQuotes(String json) {
        return json.replace("'", "\"");
    }
}
