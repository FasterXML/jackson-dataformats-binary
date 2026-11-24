package com.fasterxml.jackson.dataformat.avro;

import java.io.*;
import java.nio.file.Files;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.avro.schema.AvroSchemaGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// For [dataformats-binary#339]: file output via SequenceWriter is one Avro
// container. Test originally added for #319.
public class FileSerialization319Test extends AvroTestBase
{
    // [dataformats-binary#339]
    @Test
    public void testFileSerialization() throws Exception
    {
        final Employee employee = new Employee();
        employee.name = "Bobbee";
        employee.age = 39;
        employee.emails = new String[]{"bob@aol.com", "bobby@gmail.com"};
        employee.boss = null;

        final AvroFactory avroFactory = AvroFactory.builderWithApacheDecoder().enable(AvroGenerator.Feature.AVRO_FILE_OUTPUT).build();
        final AvroSchemaGenerator generator = new AvroSchemaGenerator();

        final AvroMapper mapper = AvroMapper.builder(avroFactory).build();
        mapper.acceptJsonFormatVisitor(Employee.class, generator);

        final AvroSchema generatedSchema = generator.getGeneratedSchema();

        final File file = Files.createTempFile("employees", ".avro").toFile();
        file.deleteOnExit();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final SequenceWriter writer = mapper.writer(generatedSchema).writeValues(out);

        // Write multiple entries, this seems to be what makes it invalid.
        writer.write(employee);
        writer.write(employee);
        writer.close();

        // Write the bytes to a file
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            out.writeTo(outputStream);
        }

        final DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(generatedSchema.getAvroSchema());

        @SuppressWarnings("resource") final DataFileReader<GenericRecord> dataFileReader = new DataFileReader<>(file, datumReader);

        GenericRecord output = dataFileReader.next();
        assertNotNull(output);
        assertEquals(output.get("name").toString(), employee.name);

        output = dataFileReader.next();
        assertNotNull(output);
        assertEquals(output.get("name").toString(), employee.name);
    }
}
