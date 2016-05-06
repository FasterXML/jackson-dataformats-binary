package perf;

import java.io.*;

import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public final class SerPerf extends PerfBase
{
    /*
    /**********************************************************
    /* Actual test
    /**********************************************************
     */

    private final int REPS;

    private final GenericDatumWriter<GenericRecord> WRITER;
    
    private SerPerf() throws Exception
    {
        // Let's try to guesstimate suitable size...
        REPS = 9000;
        WRITER = new GenericDatumWriter<GenericRecord>(itemSchema.getAvroSchema());
    }
    
    public void test()
        throws Exception
    {
        int i = 0;
        int sum = 0;

        ByteArrayOutputStream result = new ByteArrayOutputStream();

        final MediaItem item = buildItem();
        final ObjectWriter writer = itemWriter;
        final GenericRecord itemRecord = itemToRecord(item);
        final ObjectWriter jsonWriter = new ObjectMapper()
            .writerFor(MediaItem.class);
        
        while (true) {
//            Thread.sleep(150L);
            ++i;
            int round = (i % 3);

            // override?
            round = 0;

            long curr = System.currentTimeMillis();
            int len;
            String msg;

            switch (round) {

            case 0:
                msg = "Serialize, Avro/Jackson";
                len = testObjectSer(writer, item, REPS+REPS, result);
                sum += len;
                break;
            case 1:
                msg = "Serialize, Avro/STD";
                len = testAvroSer(itemRecord, REPS+REPS, result);
                sum += len;
                break;
            case 2:
                msg = "Serialize, JSON";
                len = testObjectSer(jsonWriter, item, REPS+REPS, result);
                sum += len;
                break;
            default:
                throw new Error("Internal error");
            }

            curr = System.currentTimeMillis() - curr;
            if (round == 0) {  System.out.println(); }
            System.out.println("Test '"+msg+"' -> "+curr+" msecs ("+len+" / "+(sum & 0xFF)+").");
            if ((i & 0x1F) == 0) { // GC every 64 rounds
                System.out.println("[GC]");
                Thread.sleep(20L);
                System.gc();
                Thread.sleep(20L);
            }
        }
    }

    private int testObjectSer(ObjectWriter writer, MediaItem value, int reps,
            ByteArrayOutputStream result)
        throws Exception
    {
        for (int i = 0; i < reps; ++i) {
            result.reset();
            writer.writeValue(result, value);
        }
        return result.size();
    }
    
    private int testAvroSer(GenericRecord value, int reps,
            ByteArrayOutputStream result)
        throws Exception
    {
        BinaryEncoder avroEncoder = null;
        for (int i = 0; i < reps; ++i) {
            result.reset();
            // reuse?
            //avroEncoder = ENCODER_FACTORY.binaryEncoder(result, null);
            avroEncoder = ENCODER_FACTORY.binaryEncoder(result, avroEncoder);
            WRITER.write(value, avroEncoder);
            avroEncoder.flush();
        }
        return result.size(); // just to get some non-optimizable number
    }
    
    public static void main(String[] args) throws Exception
    {
        new SerPerf().test();
    }
}
