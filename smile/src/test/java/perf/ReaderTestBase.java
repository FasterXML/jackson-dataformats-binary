package perf;

import java.io.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;

abstract class ReaderTestBase
{
    protected final static int WARMUP_ROUNDS = 5;

    protected String _desc1, _desc2;
    
    protected int hash;
    protected long startMeasure = System.currentTimeMillis() + 5000L;
    protected int roundsDone = 0;
    protected int REPS;
    private double[] timeMsecs;

    protected abstract int targetSizeMegs();
    
    protected void testFromBytes(ObjectMapper mapper1, String desc1,
            Object inputValue1, Class<?> inputClass1,
            ObjectMapper mapper2, String desc2,
            Object inputValue2, Class<?> inputClass2)
        throws Exception
    {
        final byte[] byteInput1 = mapper1.writeValueAsBytes(inputValue1);
        final byte[] byteInput2 = mapper2.writeValueAsBytes(inputValue2);
        // Let's try to guestimate suitable size... to get to N megs to process
        REPS = (int) ((double) (targetSizeMegs() * 1000 * 1000) / (double) byteInput1.length);

        // sanity check:
        /*T1 back1 =*/ mapper1.readValue(byteInput1, inputClass1);
        /*T2 back2 =*/ mapper2.readValue(byteInput2, inputClass2);
        System.out.println("Input successfully round-tripped for both input types");

        _desc1 = String.format("%s (%d bytes)", desc1, byteInput1.length);
        _desc2 = String.format("%s (%d bytes)", desc2, byteInput2.length);

        System.out.printf("Read %d/%d bytes to bind; will do %d repetitions to get about %d megs\n",
                byteInput1.length, byteInput2.length, REPS, targetSizeMegs());
        
        doTest(mapper1, byteInput1, inputClass1, mapper2, byteInput2, inputClass2);
    }
    
    protected void doTest(ObjectMapper mapper1, byte[] byteInput1, Class<?> inputClass1,
            ObjectMapper mapper2, byte[] byteInput2, Class<?> inputClass2)
        throws Exception
    {
        System.out.print("Warming up");

        final ObjectReader jsonReader = mapper1.reader()
                .forType(inputClass1);
        final ObjectReader arrayReader = mapper2.reader()
                .forType(inputClass2);
        
        int i = 0;
        final int TYPES = 2;

        timeMsecs = new double[TYPES];
        
        while (true) {
            Thread.sleep(100L);
            final int type = (i++ % TYPES);

            String msg;
            double msesc;
            
            switch (type) {
            case 0:
                msg = _desc1;
                msesc = testDeser1(REPS, byteInput1, jsonReader);
                break;
            case 1:
                msg = _desc2;
                msesc = testDeser2(REPS, byteInput2, arrayReader);
                break;
            default:
                throw new Error();
            }
            updateStats(type, (i % 17) == 0, msg, msesc);
        }
    }

    private void updateStats(int type, boolean doGc, String msg, double msecs)
        throws Exception
    {
        final boolean lf = (type == (timeMsecs.length - 1));

        if (startMeasure == 0L) { // skip first N seconds
            timeMsecs[type] += msecs;
        } else {
            if (lf) {
                if (System.currentTimeMillis() >= startMeasure) {
                    startMeasure = 0L;
                    System.out.println(" complete!");
                } else {
                    System.out.print(".");
                }
            }
            return;
        }

        System.out.printf("Test '%s' [hash: 0x%s] -> %.1f msecs\n", msg, Integer.toHexString(hash), msecs);
        if (lf) {
            ++roundsDone;
            if ((roundsDone % 3) == 0 ) {
                double den = (double) roundsDone;
                System.out.printf("Averages after %d rounds (%s/%s): %.1f / %.1f msecs\n",
                        (int) den, _desc1, _desc2,
                        timeMsecs[0] / den, timeMsecs[1] / den);
            }
            System.out.println();
        }
        if (doGc) {
            System.out.println("[GC]");
            Thread.sleep(100L);
            System.gc();
            Thread.sleep(100L);
        }
    }

    protected double testDeser1(int reps, byte[] input, ObjectReader reader) throws Exception {
        return _testDeser(reps, input, reader);
    }
    protected double testDeser2(int reps, byte[] input, ObjectReader reader) throws Exception {
        return _testDeser(reps, input, reader);
    }
    
    protected final double _testDeser(int reps, byte[] input, ObjectReader reader) throws Exception
    {
        long start = System.nanoTime();
        Object result = null;
        while (--reps >= 0) {
            result = reader.readValue(input);
        }
        hash = result.hashCode();
        // return microseconds
        return _msecsFromNanos(System.nanoTime() - start);
    }

    protected double testDeser1(int reps, String input, ObjectReader reader) throws Exception {
        return _testDeser(reps, input, reader);
    }

    protected double testDeser2(int reps, String input, ObjectReader reader) throws Exception {
        return _testDeser(reps, input, reader);
    }
    
    protected final double _testDeser(int reps, String input, ObjectReader reader) throws Exception
    {
        long start = System.nanoTime();
        Object result = null;
        while (--reps >= 0) {
            result = reader.readValue(input);
        }
        hash = result.hashCode();
        return _msecsFromNanos(System.nanoTime() - start);
    }

    protected final double _msecsFromNanos(long nanos) {
        return (nanos / 1000000.0);
    }
    
    protected static byte[] readAll(String filename) throws IOException
    {
        File f = new File(filename);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) f.length());
        byte[] buffer = new byte[4000];
        int count;
        FileInputStream in = new FileInputStream(f);
        
        while ((count = in.read(buffer)) > 0) {
            bytes.write(buffer, 0, count);
        }
        in.close();
        return bytes.toByteArray();
    }

    protected static byte[] convert(byte[] input, JsonFactory inputF, JsonFactory outputF)
            throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(input.length);
        JsonParser p = inputF.createParser(input);
        JsonGenerator g = outputF.createGenerator(bytes);

        while (p.nextToken() != null) {
            g.copyCurrentEvent(p);
        }
        p.close();
        g.close();
        
        return bytes.toByteArray();
    }
}
