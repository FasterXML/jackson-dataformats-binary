package perf;

import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

/**
 * Micro-benchmark for comparing performance of bean deserialization
 */
public final class DeserPerf extends PerfBase
{
    private final int REPS;

//    private final DecoderFactory DECODER_FACTORY = DecoderFactory.get();
    
    private final GenericDatumReader<GenericRecord> READER;
    
    private DeserPerf() {
        // Let's try to guestimate suitable size
        REPS = 13000;
        READER = new GenericDatumReader<GenericRecord>(itemSchema.getAvroSchema());
    }
    
    public void test()
        throws Exception
    {
        int sum = 0;

        final MediaItem item = buildItem();
        
        // Use Jackson?
//        byte[] json = jsonMapper.writeValueAsBytes(item);
        byte[] avro =  itemToBytes(item);
        
        System.out.println("Warmed up: data size is "+avro.length+" bytes; "+REPS+" reps -> "
                +((REPS * avro.length) >> 10)+" kB per iteration");
        System.out.println();

        final ObjectReader avroReader = itemReader;
        final ObjectMapper jsonMapper = new ObjectMapper();
        final ObjectReader jsonReader = jsonMapper.readerFor(MediaItem.class);
        
        int round = 0;
        while (true) {
//            try {  Thread.sleep(100L); } catch (InterruptedException ie) { }

            long curr = System.currentTimeMillis();
            String msg;
            round = (++round % 3);

//if (true) round = 2; 
            
            boolean lf = (round == 0);

            switch (round) {
            case 0:
                msg = "Deserialize, Avro/Jackson";
                sum += testDeser(avroReader, avro, REPS);
                break;
            case 1:
                msg = "Deserialize, Avro/STD";
                sum += testDeserAvro(avro, REPS);
                break;
            case 2:
                msg = "Deserialize, JSON/Jackson";
                sum += testDeser(jsonReader, jsonMapper.writeValueAsBytes(item), REPS);
                break;

            default:
                throw new Error("Internal error");
            }

            curr = System.currentTimeMillis() - curr;
            if (lf) {
                System.out.println();
            }
            System.out.println("Test '"+msg+"' -> "+curr+" msecs ("
                               +(sum & 0xFF)+").");
        }
    }

    protected int testDeser(ObjectReader reader, byte[] input, int reps)
        throws Exception
    {
        MediaItem item = null;
        for (int i = 0; i < reps; ++i) {
            item = reader.readValue(input, 0, input.length);
        }
        return item.hashCode(); // just to get some non-optimizable number
    }

    protected int testDeserAvro(byte[] input, int reps)
        throws Exception
    {
        BinaryDecoder decoder = null;
        GenericRecord rec = null;
        for (int i = 0; i < reps; ++i) {
            decoder = DECODER_FACTORY.binaryDecoder(input, decoder);
            rec = READER.read(null, decoder);
        }
        return rec.hashCode(); // just to get some non-optimizable number
    }
    
    public static void main(String[] args) throws Exception
    {
        new DeserPerf().test();
    }
}
