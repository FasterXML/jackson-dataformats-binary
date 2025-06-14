package com.fasterxml.jackson.dataformat.avro.schema;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.annotation.AvroNamespace;

import org.apache.avro.Schema;
import org.apache.avro.reflect.Union;

import static org.assertj.core.api.Assertions.assertThat;

public class PolymorphicTypeAnnotationsTest {

    private static final AvroMapper MAPPER = AvroMapper.builder().build();
    // it is easier maintain string schema representation when namespace is constant, rather than being inferred from this class package name
    private static final String TEST_NAMESPACE = "test";

    @JsonSubTypes({
            @JsonSubTypes.Type(value = Cat.class),
            @JsonSubTypes.Type(value = Dog.class),
    })
    interface AnimalInterface {
    }

    static abstract class AbstractMammal implements AnimalInterface {
        public int legs;
    }

    static class Cat extends AbstractMammal {
        public String color;
    }

    static class Dog extends AbstractMammal {
        public int size;
    }

    @Test
    public void subclasses_of_interface_test() throws Exception {
        // GIVEN
        final Schema catSchema = MAPPER.schemaFor(Cat.class).getAvroSchema();
        final Schema dogSchema = MAPPER.schemaFor(Dog.class).getAvroSchema();

        // WHEN
        Schema actualSchema = MAPPER.schemaFor(AnimalInterface.class).getAvroSchema();

        // THEN
        assertThat(actualSchema.getType()).isEqualTo(Schema.Type.UNION);
        // Because AnimalInterface is interface and AbstractMammal is abstract, they are not expected to be among types in union
        assertThat(actualSchema.getTypes()).containsExactlyInAnyOrder(catSchema, dogSchema);
    }

    @JsonSubTypes({
            @JsonSubTypes.Type(value = Apple.class),
            @JsonSubTypes.Type(value = Pear.class),
    })
    @AvroNamespace(TEST_NAMESPACE) // @AvroNamespace makes it easier to create schema string representation
    static class Fruit {
        public boolean eatable;
    }

    private static final String FRUIT_ITSELF_SCHEMA_STR = "{\"type\":\"record\",\"name\":\"Fruit\",\"namespace\":\"test\",\"fields\":[{\"name\":\"eatable\",\"type\":\"boolean\"}]}";

    static class Apple extends Fruit {
        public String color;
    }

    static class Pear extends Fruit {
        public int seeds;
    }

    @Test
    public void jsonSubTypes_on_concrete_class_test() throws Exception {
        // GIVEN
        final Schema fruitItselfSchema = MAPPER.schemaFrom(FRUIT_ITSELF_SCHEMA_STR).getAvroSchema();
        final Schema appleSchema = MAPPER.schemaFor(Apple.class).getAvroSchema();
        final Schema pearSchema = MAPPER.schemaFor(Pear.class).getAvroSchema();

        // WHEN
        Schema actualSchema = MAPPER.schemaFor(Fruit.class).getAvroSchema();

        // THEN
        assertThat(actualSchema.getType()).isEqualTo(Schema.Type.UNION);
        assertThat(actualSchema.getTypes()).containsExactlyInAnyOrder(fruitItselfSchema, appleSchema, pearSchema);
    }

    @JsonSubTypes({
            @JsonSubTypes.Type(value = LandVehicle.class),
            @JsonSubTypes.Type(value = AbstractWaterVehicle.class),
    })
    @AvroNamespace(TEST_NAMESPACE)
    static class Vehicle {
    }

    private static final String VEHICLE_ITSELF_SCHEMA_STR = "{\"type\":\"record\",\"name\":\"Vehicle\",\"namespace\":\"test\",\"fields\":[]}";

    @JsonSubTypes({
            @JsonSubTypes.Type(value = Car.class),
            @JsonSubTypes.Type(value = MotorCycle.class),
    })
    @AvroNamespace(TEST_NAMESPACE)
    static class LandVehicle extends Vehicle {
    }

    private static final String LAND_VEHICLE_ITSELF_SCHEMA_STR = "{\"type\":\"record\",\"name\":\"LandVehicle\",\"namespace\":\"test\",\"fields\":[]}";

    static class Car extends LandVehicle {
    }

    static class MotorCycle extends LandVehicle {
    }

    @JsonSubTypes({
            @JsonSubTypes.Type(value = Boat.class),
            @JsonSubTypes.Type(value = Submarine.class),
    })
    static abstract class AbstractWaterVehicle extends Vehicle {
        public int propellers;
    }

    static class Boat extends AbstractWaterVehicle {
    }

    static class Submarine extends AbstractWaterVehicle {
    }

    @Test
    public void jsonSubTypes_of_jsonSubTypes_test() throws Exception {
        // GIVEN
        final Schema vehicleItselfSchema = MAPPER.schemaFrom(VEHICLE_ITSELF_SCHEMA_STR).getAvroSchema();
        final Schema landVehicleItselfSchema = MAPPER.schemaFrom(LAND_VEHICLE_ITSELF_SCHEMA_STR).getAvroSchema();
        final Schema carSchema = MAPPER.schemaFor(Car.class).getAvroSchema();
        final Schema motorCycleSchema = MAPPER.schemaFor(MotorCycle.class).getAvroSchema();
        final Schema boatSchema = MAPPER.schemaFor(Boat.class).getAvroSchema();
        final Schema submarineSchema = MAPPER.schemaFor(Submarine.class).getAvroSchema();

        // WHEN
        Schema actualSchema = MAPPER.schemaFor(Vehicle.class).getAvroSchema();

        // THEN
        assertThat(actualSchema.getType()).isEqualTo(Schema.Type.UNION);
        assertThat(actualSchema.getTypes()).containsExactlyInAnyOrder(
                vehicleItselfSchema,
                landVehicleItselfSchema, carSchema, motorCycleSchema,
                // AbstractWaterVehicle is not here, because it is abstract
                boatSchema, submarineSchema);
    }

    // Helium is twice in subtypes hierarchy, once as ElementInterface subtype and second time as subtype
    // of AbstractGas subtype. This situation may result in
    // "Failed to generate `AvroSchema` for  ...., problem: (AvroRuntimeException) Duplicate in union:com.fasterxml...PolymorphicTypeAnnotationsTest.Helium"
    // error.
    @JsonSubTypes({
            @JsonSubTypes.Type(value = AbstractGas.class),
            @JsonSubTypes.Type(value = Helium.class),
    })
    private interface ElementInterface {
    }

    @JsonSubTypes({
            @JsonSubTypes.Type(value = Helium.class),
            @JsonSubTypes.Type(value = Oxygen.class),
    })
    static abstract class AbstractGas implements ElementInterface {
        public int atomicMass;
    }

    private static class Helium extends AbstractGas {
    }

    private static class Oxygen extends AbstractGas {
    }

    @Test
    public void class_is_referenced_twice_in_hierarchy_test() throws Exception {
        // GIVEN
        final Schema heliumSchema = MAPPER.schemaFor(Helium.class).getAvroSchema();
        final Schema oxygenSchema = MAPPER.schemaFor(Oxygen.class).getAvroSchema();

        // WHEN
        Schema actualSchema = MAPPER.schemaFor(ElementInterface.class).getAvroSchema();

        // THEN
        assertThat(actualSchema.getType()).isEqualTo(Schema.Type.UNION);
        // ElementInterface and AbstractGas are not concrete classes they are not expected to be among types in union
        assertThat(actualSchema.getTypes()).containsExactlyInAnyOrder(heliumSchema, oxygenSchema);
    }

    @JsonSubTypes({
            // Base class being explicitly in @JsonSubTypes led to StackOverflowError exception.
            @JsonSubTypes.Type(value = Image.class),
            @JsonSubTypes.Type(value = Jpeg.class),
            @JsonSubTypes.Type(value = Png.class),
    })
    @AvroNamespace(TEST_NAMESPACE) // @AvroNamespace makes it easier to create schema string representation
    static class Image {
    }

    private static final String IMAGE_ITSELF_SCHEMA_STR = "{\"type\":\"record\",\"name\":\"Image\",\"namespace\":\"test\",\"fields\":[]}";

    static class Jpeg extends Image {
    }

    static class Png extends Image {
    }

    @Test
    public void base_class_explicitly_in_JsonSubTypes_annotation_test() throws Exception {
        // GIVEN
        final Schema imageItselfSchema = MAPPER.schemaFrom(IMAGE_ITSELF_SCHEMA_STR).getAvroSchema();
        final Schema jpegSchema = MAPPER.schemaFor(Jpeg.class).getAvroSchema();
        final Schema pngSchema = MAPPER.schemaFor(Png.class).getAvroSchema();

        // WHEN
        Schema actualSchema = MAPPER.schemaFor(Image.class).getAvroSchema();

        // THEN
        assertThat(actualSchema.getType()).isEqualTo(Schema.Type.UNION);
        assertThat(actualSchema.getTypes()).containsExactlyInAnyOrder(imageItselfSchema, jpegSchema, pngSchema);
    }
    
    @Union({
            // Base class being explicitly in @Union led to StackOverflowError exception.
            Sport.class,
            Football.class, Basketball.class})
    @AvroNamespace(TEST_NAMESPACE) // @AvroNamespace makes it easier to create schema string representation
    static class Sport {
    }

    private static final String SPORT_ITSELF_SCHEMA_STR = "{\"type\":\"record\",\"name\":\"Sport\",\"namespace\":\"test\",\"fields\":[]}";

    static class Football extends Sport {
    }

    static class Basketball extends Sport {
    }

    @Test
    public void base_class_explicitly_in_Union_annotation_test() throws Exception {
        // GIVEN
        final Schema sportItselfSchema = MAPPER.schemaFrom(SPORT_ITSELF_SCHEMA_STR).getAvroSchema();
        final Schema footballSchema = MAPPER.schemaFor(Football.class).getAvroSchema();
        final Schema basketballSchema = MAPPER.schemaFor(Basketball.class).getAvroSchema();

        // WHEN
        Schema actualSchema = MAPPER.schemaFor(Sport.class).getAvroSchema();

        //System.out.println("Sport schema:\n" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getType()).isEqualTo(Schema.Type.UNION);
        assertThat(actualSchema.getTypes()).containsExactlyInAnyOrder(sportItselfSchema, footballSchema, basketballSchema);
    }

    @Union({
            // Interface being explicitly in @Union led to StackOverflowError exception.
            DocumentInterface.class,
            Word.class, Excel.class})
    interface DocumentInterface {
    }

    static class Word implements DocumentInterface {
    }

    static class Excel implements DocumentInterface {
    }

    @Test
    public void interface_explicitly_in_Union_annotation_test() throws Exception {
        // GIVEN
        final Schema wordSchema = MAPPER.schemaFor(Word.class).getAvroSchema();
        final Schema excelSchema = MAPPER.schemaFor(Excel.class).getAvroSchema();

        // WHEN
        Schema actualSchema = MAPPER.schemaFor(DocumentInterface.class).getAvroSchema();

        //System.out.println("Document schema:\n" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getType()).isEqualTo(Schema.Type.UNION);
        assertThat(actualSchema.getTypes()).containsExactlyInAnyOrder(wordSchema, excelSchema);
    }
}
