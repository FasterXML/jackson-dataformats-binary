package perf;

import java.util.ArrayList;
import java.util.List;

public class  MediaItem
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

     static MediaItem buildItem()
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
}

enum Size { SMALL, LARGE };

class Image
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
} 

enum Player { JAVA, FLASH; }

class Media {

    public String uri;
    public String title;        // Can be unset.
    public int width;
    public int height;
    public String format;
    public long duration;
    public long size;
    public int bitrate;         // Can be unset.
//    public boolean hasBitrate;

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
}
