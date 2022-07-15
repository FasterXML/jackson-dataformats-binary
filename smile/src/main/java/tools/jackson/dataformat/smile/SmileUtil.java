package tools.jackson.dataformat.smile;

/**
 * Class for miscellaneous helper methods.
 */
public class SmileUtil
{
    public static int zigzagEncode(int input) {
        // canonical, avoids branching, which matters more than alu ops
        return (input << 1) ^  (input >> 31);
        /*
        if (input < 0) {
            return (input << 1) ^ -1;
        }
        return (input << 1);
        */
    }

    public static int zigzagDecode(int encoded) {
        // canonical, avoids branching
        return (encoded >>> 1) ^ (-(encoded & 1));
        /*
        if ((encoded & 1) == 0) {
            return (encoded >>> 1);
        }
        return (encoded >>> 1) ^ -1;
        */
    }
    
    public static long zigzagEncode(long input) {
        // Canonical version
        return (input << 1) ^  (input >> 63);
        /*
        if (input < 0L) {
            return (input << 1) ^ -1L;
        }
        return (input << 1);
        */
    }

    public static long zigzagDecode(long encoded) {
        // canonical:
        return (encoded >>> 1) ^ (-(encoded & 1));
        /*
        if ((encoded & 1) == 0) {
            return (encoded >>> 1);
        }
        return (encoded >>> 1) ^ -1L;
        */
    }
}
