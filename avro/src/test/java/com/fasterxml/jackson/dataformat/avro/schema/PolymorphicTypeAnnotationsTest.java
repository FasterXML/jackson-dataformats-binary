package com.fasterxml.jackson.dataformat.avro.schema;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import com.fasterxml.jackson.dataformat.avro.annotation.AvroNamespace;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class PolymorphicTypeAnnotationsTest {

    private static final AvroMapper MAPPER = AvroMapper.builder().build();
    // it is easier maintain string schema representation when namespace is constant, rather than being inferred from this class package name
    private static final String TEST_NAMESPACE = "test";

    @JsonSubTypes({
            @JsonSubTypes.Type(value = Cat.class),
            @JsonSubTypes.Type(value = Dog.class),
    })
    private interface AnimalInterface {
    }

    private static abstract class AbstractMammal implements AnimalInterface {
        public int legs;
    }

    private static class Cat extends AbstractMammal {
        public String color;
    }

    private static class Dog extends AbstractMammal {
        public int size;
    }

    @Test
    public void subclasses_of_interface_test() throws JsonMappingException {
        // GIVEN
        final Schema catSchema = MAPPER.schemaFor(Cat.class).getAvroSchema();
        final Schema dogSchema = MAPPER.schemaFor(Dog.class).getAvroSchema();

        // WHEN
        Schema actualSchema = MAPPER.schemaFor(AnimalInterface.class).getAvroSchema();

        System.out.println("Animal schema:\n" + actualSchema.toString(true));

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
    private static class Fruit {
        public boolean eatable;
    }

    private static final String FRUIT_ITSELF_SCHEMA_STR = "{\"type\":\"record\",\"name\":\"Fruit\",\"namespace\":\"test\",\"fields\":[{\"name\":\"eatable\",\"type\":\"boolean\"}]}";

    private static class Apple extends Fruit {
        public String color;
    }

    private static class Pear extends Fruit {
        public int seeds;
    }

    @Test
    public void subclasses_of_concrete_class_test() throws IOException {
        // GIVEN
        final Schema fruitItselfSchema = MAPPER.schemaFrom(FRUIT_ITSELF_SCHEMA_STR).getAvroSchema();
        final Schema appleSchema = MAPPER.schemaFor(Apple.class).getAvroSchema();
        final Schema pearSchema = MAPPER.schemaFor(Pear.class).getAvroSchema();

        // WHEN
        Schema actualSchema = MAPPER.schemaFor(Fruit.class).getAvroSchema();

        System.out.println("Fruit schema:\n" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getType()).isEqualTo(Schema.Type.UNION);
        assertThat(actualSchema.getTypes()).containsExactlyInAnyOrder(fruitItselfSchema, appleSchema, pearSchema);
    }

    @JsonSubTypes({
            @JsonSubTypes.Type(value = LandVehicle.class),
            @JsonSubTypes.Type(value = AbstractWaterVehicle.class),
    })
    @AvroNamespace(TEST_NAMESPACE)
    private static class Vehicle {
    }

    private static final String VEHICLE_ITSELF_SCHEMA_STR = "{\"type\":\"record\",\"name\":\"Vehicle\",\"namespace\":\"test\",\"fields\":[]}";

    @JsonSubTypes({
            @JsonSubTypes.Type(value = Car.class),
            @JsonSubTypes.Type(value = MotorCycle.class),
    })
    @AvroNamespace(TEST_NAMESPACE)
    private static class LandVehicle extends Vehicle {
    }

    private static final String LAND_VEHICLE_ITSELF_SCHEMA_STR = "{\"type\":\"record\",\"name\":\"LandVehicle\",\"namespace\":\"test\",\"fields\":[]}";

    private static class Car extends LandVehicle {
    }

    private static class MotorCycle extends LandVehicle {
    }

    @JsonSubTypes({
            @JsonSubTypes.Type(value = Boat.class),
            @JsonSubTypes.Type(value = Submarine.class),
    })
    private static abstract class AbstractWaterVehicle extends Vehicle {
        public int propellers;
    }

    private static class Boat extends AbstractWaterVehicle {
    }

    private static class Submarine extends AbstractWaterVehicle {
    }

    @Test
    public void subclasses_of_subclasses_test() throws IOException {
        // GIVEN
        final Schema vehicleItselfSchema = MAPPER.schemaFrom(VEHICLE_ITSELF_SCHEMA_STR).getAvroSchema();
        final Schema landVehicleItselfSchema = MAPPER.schemaFrom(LAND_VEHICLE_ITSELF_SCHEMA_STR).getAvroSchema();
        final Schema carSchema = MAPPER.schemaFor(Car.class).getAvroSchema();
        final Schema motorCycleSchema = MAPPER.schemaFor(MotorCycle.class).getAvroSchema();
        final Schema boatSchema = MAPPER.schemaFor(Boat.class).getAvroSchema();
        final Schema submarineSchema = MAPPER.schemaFor(Submarine.class).getAvroSchema();

        // WHEN
        Schema actualSchema = MAPPER.schemaFor(Vehicle.class).getAvroSchema();

        System.out.println("Vehicle schema:\n" + actualSchema.toString(true));

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
    public void class_is_referenced_twice_in_hierarchy_test() throws JsonMappingException {
        // GIVEN
        final Schema heliumSchema = MAPPER.schemaFor(Helium.class).getAvroSchema();
        final Schema oxygenSchema = MAPPER.schemaFor(Oxygen.class).getAvroSchema();

        // WHEN
        Schema actualSchema = MAPPER.schemaFor(ElementInterface.class).getAvroSchema();

        System.out.println("ElementInterface schema:\n" + actualSchema.toString(true));

        // THEN
        assertThat(actualSchema.getType()).isEqualTo(Schema.Type.UNION);
        // ElementInterface and AbstractGas are not concrete classes they are not expected to be among types in union
        assertThat(actualSchema.getTypes()).containsExactlyInAnyOrder(heliumSchema, oxygenSchema);
    }

}
