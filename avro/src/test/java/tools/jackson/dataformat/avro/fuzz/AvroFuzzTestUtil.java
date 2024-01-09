package tools.jackson.dataformat.avro.fuzz;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AvroFuzzTestUtil
{
    public static byte[] readResource(String ref)
    {
       ByteArrayOutputStream bytes = new ByteArrayOutputStream();
       final byte[] buf = new byte[4000];

       InputStream in = AvroFuzzTestUtil.class.getResourceAsStream(ref);
       if (in != null) {
           try {
               int len;
               while ((len = in.read(buf)) > 0) {
                   bytes.write(buf, 0, len);
               }
               in.close();
           } catch (IOException e) {
               throw new RuntimeException("Failed to read resource '"+ref+"': "+e);
           }
       }
       if (bytes.size() == 0) {
           throw new IllegalArgumentException("Failed to read resource '"+ref+"': empty resource?");
       }
       return bytes.toByteArray();
    }
}
