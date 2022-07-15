package tools.jackson.dataformat.ion.polymorphism;

import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;
import com.amazon.ion.util.Equivalence;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import tools.jackson.dataformat.ion.IonGenerator;
import tools.jackson.dataformat.ion.IonObjectMapper;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class SerializationAnnotationsTest {

    private static final String SUBCLASS_TYPE_NAME =
            SerializationAnnotationsTest.Subclass.class.getTypeName();

    private static final IonValue SUBCLASS_TYPED_AS_PROPERTY = asIonValue(
            "{" +
                    "  '@class':\"" + SUBCLASS_TYPE_NAME + "\"," +
                    "  someString:\"some value\"," +
                    "  anInt:42" +
                    "}");

    private static final IonValue SUBCLASS_TYPED_BY_ANNOTATION = asIonValue(
            "'" + SUBCLASS_TYPE_NAME + "'::{" +
                    "  someString:\"some value\"," +
                    "  anInt:42" +
                    "}");

    private Subclass subclass;

    @Before
    public void setup() {
        this.subclass = new Subclass("some value", 42);
    }

    @Test
    public void testNativeTypeIdsEnabledOnWriteByDefault() throws IOException {
        IonObjectMapper mapper = new IonObjectMapper();
        IonValue subclassAsIon = mapper.writeValueAsIonValue(subclass);

        assertEqualIonValues(SUBCLASS_TYPED_BY_ANNOTATION, subclassAsIon);

        BaseClass roundTripInstance = mapper.readValue(subclassAsIon, BaseClass.class);

        assertCorrectlyTypedAndFormed(subclass, roundTripInstance);
    }

    @Test
    public void testNativeTypeIdsCanBeDisabledOnWrite() throws Exception {
        IonObjectMapper mapper = IonObjectMapper.builderForTextualWriters()
                .disable(IonGenerator.Feature.USE_NATIVE_TYPE_ID)
                .build();

        IonValue subclassAsIon = mapper.writeValueAsIonValue(subclass);
        assertEqualIonValues(SUBCLASS_TYPED_AS_PROPERTY, subclassAsIon);

        BaseClass roundTripInstance = mapper.readValue(subclassAsIon, BaseClass.class);

        assertCorrectlyTypedAndFormed(subclass, roundTripInstance);
    }

    @Test
    public void testNativeTypeIdsDisabledStillReadsNativeTypesSuccessfully() throws IOException {
        IonObjectMapper writer = new IonObjectMapper(); // native type ids enabled by default

        IonValue subclassAsIon = writer.writeValueAsIonValue(subclass);

        assertEqualIonValues(SUBCLASS_TYPED_BY_ANNOTATION, subclassAsIon);

        IonObjectMapper reader = IonObjectMapper.builderForTextualWriters()
                .disable(IonGenerator.Feature.USE_NATIVE_TYPE_ID)
                .build();

        BaseClass roundTripInstance = reader.readValue(subclassAsIon, BaseClass.class);

        assertCorrectlyTypedAndFormed(subclass, roundTripInstance);
    }

    /*
    /**********************************************************
    /* Helper methods etc.
    /**********************************************************
     */


    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
    static public abstract class BaseClass { /* empty */ }

    public static class Subclass extends BaseClass {
        public String someString;
        public int anInt;

        public Subclass() {};
        public Subclass(String s, int i) {
            this.someString = s;
            this.anInt = i;
        }
    }

    private static IonValue asIonValue(final String ionStr) {
        return IonSystemBuilder.standard().build().singleValue(ionStr);
    }

    private static void assertCorrectlyTypedAndFormed(final Subclass expectedSubclass, final BaseClass actualBaseclass) {
        Assert.assertTrue(actualBaseclass instanceof Subclass);
        assertEquals(expectedSubclass, (Subclass) actualBaseclass);
    }
    private static void assertEquals(Subclass expected, Subclass actual) {
        Assert.assertEquals(expected.someString, ((Subclass) actual).someString);
        Assert.assertEquals(expected.anInt, ((Subclass) actual).anInt);
    }

    private static void assertEqualIonValues(IonValue expected, IonValue actual) {
        if (!Equivalence.ionEquals(expected, actual)) {
            String message = String.format("Expected %s but found %s",
                    expected.toPrettyString(), actual.toPrettyString());
            throw new AssertionError(message);
        }
    }
}
