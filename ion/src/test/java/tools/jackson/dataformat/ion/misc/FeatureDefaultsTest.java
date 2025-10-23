package tools.jackson.dataformat.ion.misc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.ion.*;

public class FeatureDefaultsTest
{
    // [dataformats-binary#619]
    @Test
    void testFormatFeatureDefaults() {
        IonObjectMapper mapper = IonObjectMapper.shared();
        assertTrue(mapper.isEnabled(IonReadFeature.USE_NATIVE_TYPE_ID));
        assertTrue(mapper.isEnabled(IonWriteFeature.USE_NATIVE_TYPE_ID));
    }
}
