package perf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

public class ReadPerfMediaItem extends ReaderTestBase
{
    private ReadPerfMediaItem() { }
    
    @Override
    protected int targetSizeMegs() { return 10; }

    public static void main(String[] args) throws Exception
    {
        if (args.length != 0) {
            System.err.println("Usage: java ...");
            System.exit(1);
        }
        SmileFactory sf = new SmileFactory();
        ObjectMapper m = new ObjectMapper(sf);

        final MediaItem item = MediaItem.buildItem();

        new ReadPerfMediaItem()
            .testFromBytes(
                m, "MediaItem-as-Smile1", item, MediaItem.class
                ,m, "MediaItem-as-Smile2", item, MediaItem.class
                );
    }
}
