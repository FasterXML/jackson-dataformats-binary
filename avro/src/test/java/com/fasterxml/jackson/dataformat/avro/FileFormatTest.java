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
        _testFileFormatOutput(true);
        _testFileFormatOutput(false);
    }

    private void _testFileFormatOutput(boolean useApacheImpl) throws Exception
    {
        Employee empl = new Employee();
        empl.name = "Bobbee";
        empl.age = 39;
        empl.emails = new String[] { "bob@aol.com", "bobby@gmail.com" };
        empl.boss = null;

        AvroFactory af = (useApacheImpl
                ? AvroFactory.builderWithApacheDecoder()
                : AvroFactory.builderWithNativeDecoder())
                .enable(AvroGenerator.Feature.AVRO_FILE_OUTPUT)
                .build();
        ObjectMapper mapper = new ObjectMapper(af);

        AvroSchema schema = getEmployeeSchema();
        byte[] bytes = mapper.writer(schema).writeValueAsBytes(empl);

        assertNotNull(bytes);
        assertEquals(301, bytes.length);

        DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(schema.getAvroSchema());
        @SuppressWarnings("resource")
        DataFileReader<GenericRecord> dataFileReader = new DataFileReader<>(new SeekableByteArrayInput(bytes),
                datumReader);
        GenericRecord output = dataFileReader.next();

        assertNotNull(output);
        assertEquals(output.get("name").toString(), empl.name);
    }
}
