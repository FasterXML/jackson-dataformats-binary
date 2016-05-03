package com.fasterxml.jackson.dataformat.cbor;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.core.JsonGenerator;

public class GeneratorBinaryTest //extends CBORTestBase
{
	final static int SMALL_LENGTH = 100;
	final static int LARGE_LENGTH = CBORGenerator.BYTE_BUFFER_FOR_OUTPUT + 500;

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private File binaryInputFile;
	private File cborFile;
	private File binaryOutputFile;

	@Before
	public void before() throws IOException
	{
		 binaryInputFile = tempFolder.newFile("sourceData.bin");
		 cborFile = tempFolder.newFile("cbor.bin");
		 binaryOutputFile = tempFolder.newFile("outputData.bin");
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

	private void testEncodeAndDecodeBytes(int length) throws NoSuchAlgorithmException, IOException
	{
		generateInputFile(binaryInputFile, length);
		encodeInCBOR(binaryInputFile, cborFile);
		decodeFromCborInFile(this.cborFile, this.binaryOutputFile);
		assertFileEquals(this.binaryInputFile, this.binaryOutputFile);
	}

	private void encodeInCBOR(File inputFile, File outputFile) throws NoSuchAlgorithmException, IOException
	{
		CBORFactory f = new CBORFactory();
		OutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile));
		InputStream is = new BufferedInputStream(new FileInputStream(inputFile));

		JsonGenerator gen = f.createGenerator(os);
		gen.writeBinary(is, (int) inputFile.length());

		gen.close();
		is.close();
		os.close();
	}

	private void decodeFromCborInFile(File input, File output) throws NoSuchAlgorithmException, IOException
	{
		CBORFactory f = new CBORFactory();
		OutputStream os = new FileOutputStream(output);

		InputStream is = new FileInputStream(input);
		CBORParser parser = f.createParser(is);
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

	    Assert.assertEquals(file1.length(), file2.length());

	    int ch;
	    
	    while ((ch = fis1.read()) >= 0) {
	        Assert.assertEquals(ch, fis2.read());
	    }
	    fis1.close();
	    fis2.close();
	}
}
