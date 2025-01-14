package tools.jackson.dataformat.cbor.gen;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.cbor.databind.CBORMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GeneratorBinaryTest //extends CBORTestBase
{
	final static int SMALL_LENGTH = 100;
	final static int LARGE_LENGTH = /*CBORGenerator.BYTE_BUFFER_FOR_OUTPUT*/ 16000 + 500;

	private final ObjectMapper MAPPER = CBORMapper.shared();

	@TempDir
	public File tempFolder;

	private File binaryInputFile;
	private File cborFile;
	private File binaryOutputFile;

	@BeforeEach
	public void before() throws IOException
	{
		binaryInputFile = new File(tempFolder, "sourceData.bin");
		binaryInputFile.createNewFile();

		cborFile = new File(tempFolder, "cbor.bin");
		cborFile.createNewFile();

		binaryOutputFile = new File(tempFolder, "outputData.bin");
		binaryOutputFile.createNewFile();
	}

	@Test
	public void testSmallByteArray() throws Exception
     {
         testEncodeAndDecodeBytes(SMALL_LENGTH);
     }

	@Test
	public void testLargeByteArray() throws Exception
     {
         testEncodeAndDecodeBytes(LARGE_LENGTH);
     }

	private void generateInputFile(File input, int fileSize) throws NoSuchAlgorithmException, IOException
	{
	    OutputStream os = new BufferedOutputStream(new FileOutputStream(input));
	    SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
	    byte[] temp = new byte[1024];
	    int remaining = fileSize;
	    while (remaining > 0) {
	        sr.nextBytes(temp);
	        os.write(temp, 0, Math.min(temp.length, remaining));
	        remaining -= temp.length;
	    }
	    os.close();
	}

	private void testEncodeAndDecodeBytes(int length) throws Exception
	{
		generateInputFile(binaryInputFile, length);
		encodeInCBOR(binaryInputFile, cborFile);
		decodeFromCborInFile(this.cborFile, this.binaryOutputFile);
		assertFileEquals(this.binaryInputFile, this.binaryOutputFile);
	}

	private void encodeInCBOR(File inputFile, File outputFile) throws NoSuchAlgorithmException, IOException
	{
		OutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile));
		InputStream is = new BufferedInputStream(new FileInputStream(inputFile));

		JsonGenerator gen = MAPPER.createGenerator(os);
		gen.writeBinary(is, (int) inputFile.length());

		gen.close();
		is.close();
		os.close();
	}

	private void decodeFromCborInFile(File input, File output) throws Exception
	{
		OutputStream os = new FileOutputStream(output);

		InputStream is = new FileInputStream(input);
		JsonParser parser = MAPPER.createParser(is);
		parser.nextToken();
		parser.readBinaryValue(null, os);

		parser.close();
		is.close();
		os.close();
	}

	private void assertFileEquals(File file1, File file2) throws IOException
	{
	    FileInputStream fis1 = new FileInputStream(file1);
	    FileInputStream fis2 = new FileInputStream(file2);

	    assertEquals(file1.length(), file2.length());

	    int ch;

	    while ((ch = fis1.read()) >= 0) {
	        assertEquals(ch, fis2.read());
	    }
	    fis1.close();
	    fis2.close();
	}
}
