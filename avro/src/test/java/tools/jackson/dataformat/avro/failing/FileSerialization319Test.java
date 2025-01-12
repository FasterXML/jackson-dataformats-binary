package tools.jackson.dataformat.avro.failing;

import java.io.*;
import java.nio.file.Files;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.SequenceWriter;
import tools.jackson.dataformat.avro.*;
import tools.jackson.dataformat.avro.schema.AvroSchemaGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// For [dataformats-binary#319]: not 100% certain test is valid,
// but there is need for work wrt File-backed Avro content
public class FileSerialization319Test extends AvroTestBase
{
    // [dataformats-binary#319]
    @Test
    public void testFileSerialization() throws Exception
    {
        final Employee employee = new Employee();
        employee.name = "Bobbee";
        employee.age = 39;
        employee.emails = new String[]{"bob@aol.com", "bobby@gmail.com"};
        employee.boss = null;

        final AvroFactory avroFactory = AvroFactory.builderWithApacheDecoder().enable(AvroWriteFeature.AVRO_FILE_OUTPUT).build();
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

        // This line currently throws the following exception:
        // org.apache.avro.AvroRuntimeException: java.io.IOException: Invalid sync!
        output = dataFileReader.next();
        assertNotNull(output);
        assertEquals(output.get("name").toString(), employee.name);
    }
}
