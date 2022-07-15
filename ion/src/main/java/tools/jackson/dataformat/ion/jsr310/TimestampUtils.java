package tools.jackson.dataformat.ion.jsr310;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

import com.amazon.ion.Timestamp;

final class TimestampUtils {

    private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final BigDecimal ONE_BILLION = new BigDecimal("1000000000");
    
    private TimestampUtils() {}
    
    static Timestamp toTimestamp(Instant instant, ZoneOffset offset) {
        final Integer offsetMinutes = offset == null ? null 
                : secondsToMinutes(offset.getTotalSeconds());
        
        return Timestamp.forMillis(getFractionalMillis(instant), offsetMinutes);
    }

    static Instant toInstant(Timestamp timestamp) {
        final BigDecimal decSeconds = timestamp.getDecimalMillis().divide(ONE_THOUSAND);
        final long epocSeconds = decSeconds.longValue();
        final long nanoAdjustment = decSeconds.subtract(BigDecimal.valueOf(epocSeconds))
                .multiply(ONE_BILLION)
                .longValue();

        return Instant.ofEpochSecond(epocSeconds, nanoAdjustment);
    }

    static BigDecimal getFractionalSeconds(Instant instant) {
        final BigDecimal epochSeconds = BigDecimal.valueOf(instant.getEpochSecond());
        final BigDecimal nanos = BigDecimal.valueOf(instant.getNano());

        return epochSeconds.add(nanos.divide(ONE_BILLION));
    }

    static BigDecimal getFractionalMillis(Instant instant) {
        final BigDecimal epochSeconds = BigDecimal.valueOf(instant.getEpochSecond());
        final BigDecimal nanos = BigDecimal.valueOf(instant.getNano());

        return epochSeconds.multiply(ONE_THOUSAND)
                .add(nanos.divide(ONE_MILLION));
    }
    
    //From https://github.com/FasterXML/jackson-modules-java8
    static Instant fromFractionalSeconds(BigDecimal seconds) {
        // Complexity is here to workaround unbounded latency in some BigDecimal operations.
        //   https://github.com/FasterXML/jackson-databind/issues/2141

        long secondsOnly;
        int nanosOnly;

        BigDecimal nanoseconds = seconds.scaleByPowerOfTen(9);
        if (nanoseconds.precision() - nanoseconds.scale() <= 0) {
            // There are no non-zero digits to the left of the decimal point.
            // This protects against very negative exponents.
            secondsOnly = nanosOnly = 0;
        }
        else if (seconds.scale() < -63) {
            // There would be no low-order bits once we chop to a long.
            // This protects against very positive exponents.
            secondsOnly = nanosOnly = 0;
        }
        else {
            // Now we know that seconds has reasonable scale, we can safely chop it apart.
            secondsOnly = seconds.longValue();
            nanosOnly = nanoseconds.subtract(new BigDecimal(secondsOnly).scaleByPowerOfTen(9)).intValue();

            if (secondsOnly < 0 && secondsOnly > Instant.MIN.getEpochSecond()) {
                // Issue #69 and Issue #120: avoid sending a negative adjustment to the Instant constructor, we want this as the actual nanos
                nanosOnly = Math.abs(nanosOnly);
            }
        }

        return Instant.ofEpochSecond(secondsOnly, nanosOnly)    ;
    }
    
    private static int secondsToMinutes(int seconds) {
        return Math.floorDiv(seconds, 60);
    }
}
