package com.fasterxml.jackson.dataformat.avro;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.SeekableByteArrayInput;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;

import com.fasterxml.jackson.databind.ObjectMapper;

// for [dataformats-binary#15]
public class FileFormatTest extends AvroTestBase
{
    public void testFileFormatOutput() throws Exception
    {
        _testFileFormatOutput(AvroFactory.builderWithNativeDecoder().build());
        _testFileFormatOutput(AvroFactory.builderWithApacheDecoder().build());
    }

    private void _testFileFormatOutput(AvroFactory af) throws Exception
    {
        Employee empl = new Employee();
        empl.name = "Bobbee";
        empl.age = 39;
        empl.emails = new String[] { "bob@aol.com", "bobby@gmail.com" };
        empl.boss = null;

        ObjectMapper mapper = new ObjectMapper(af.rebuild()
                .configure(AvroGenerator.Feature.AVRO_FILE_OUTPUT, true).build());

        AvroSchema schema = getEmployeeSchema();
        byte[] bytes = mapper.writer(schema).writeValueAsBytes(empl);

        assertNotNull(bytes);
        assertEquals(301, bytes.length);

        DatumReader<GenericRecord> datumReader = new GenericDatumReader<GenericRecord>(schema.getAvroSchema());
        @SuppressWarnings("resource")
        DataFileReader<GenericRecord> dataFileReader = new DataFileReader<GenericRecord>(new SeekableByteArrayInput(bytes),
                datumReader);
        GenericRecord output = dataFileReader.next();

        assertNotNull(output);
        assertEquals(output.get("name").toString(), empl.name);
    }
}
