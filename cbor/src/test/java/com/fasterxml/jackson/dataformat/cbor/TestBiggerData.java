package com.fasterxml.jackson.dataformat.cbor;

import java.util.*;

import com.fasterxml.jackson.databind.*;

/**
 * Bigger test to try to do smoke-testing of overall functionality,
 * using more sizable (500k of JSON, 200k of encoded data) dataset.
 * Should tease out at least some of boundary conditions.
 */
public class TestBiggerData extends CBORTestBase
{
	static class Citm
	{
		public Map<Integer,String> areaNames;
		public Map<Integer,String> audienceSubCategoryNames;
		public Map<Integer,String> blockNames;
		public Map<Integer,String> seatCategoryNames;
		public Map<Integer,String> subTopicNames;
		public Map<Integer,String> subjectNames;
		public Map<Integer,String> topicNames;
		public Map<Integer,int[]> topicSubTopics;
		public Map<String,String> venueNames;

		public Map<Integer,Event> events;
		public List<Performance> performances;
	}

	static class Event
	{
		public int id;
		public String name;
		public String description;
		public String subtitle;
		public String logo;
		public int subjectCode;
		public int[] topicIds;
		public LinkedHashSet<Integer> subTopicIds;
	}

	static class Performance
	{
		public int id;
		public int eventId;
		public String name;
		public String description;
		public String logo;

		public List<Price> prices;
		public List<SeatCategory> seatCategories;

		public long start;
		public String seatMapImage;
		public String venueCode;
}

	static class Price {
		public int amount;
		public int audienceSubCategoryId;
		public int seatCategoryId;
	}

	static class SeatCategory {
		public int seatCategoryId;
		public List<Area> areas;
	}

	static class Area {
		public int areaId;
		public int[] blockIds;
	}

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

	final ObjectMapper MAPPER = new ObjectMapper();
	
	public void testReading() throws Exception
	{
		Citm citm0 = MAPPER.readValue(getClass().getResourceAsStream("/data/citm_catalog.json"),
				Citm.class);

		ObjectMapper mapper = cborMapper();
		byte[] cbor = mapper.writeValueAsBytes(citm0);

		Citm citm = mapper.readValue(cbor, Citm.class);
		
		assertNotNull(citm);
		assertNotNull(citm.areaNames);
		assertEquals(17, citm.areaNames.size());
		assertNotNull(citm.events);
		assertEquals(184, citm.events.size());

		assertNotNull(citm.seatCategoryNames);
		assertEquals(64, citm.seatCategoryNames.size());
		assertNotNull(citm.subTopicNames);
		assertEquals(19, citm.subTopicNames.size());
		assertNotNull(citm.subjectNames);
		assertEquals(0, citm.subjectNames.size());
		assertNotNull(citm.topicNames);
		assertEquals(4, citm.topicNames.size());
		assertNotNull(citm.topicSubTopics);
		assertEquals(4, citm.topicSubTopics.size());
		assertNotNull(citm.venueNames);
		assertEquals(1, citm.venueNames.size());
	}

	public void testRoundTrip() throws Exception
	{
		Citm citm0 = MAPPER.readValue(getClass().getResourceAsStream("/data/citm_catalog.json"),
				Citm.class);
		ObjectMapper mapper = cborMapper();
		byte[] cbor = mapper.writeValueAsBytes(citm0);

		Citm citm = mapper.readValue(cbor, Citm.class);

		byte[] smile1 = mapper.writeValueAsBytes(citm);
		Citm citm2 = mapper.readValue(smile1, Citm.class);
		byte[] smile2 = mapper.writeValueAsBytes(citm2);

		assertEquals(smile1.length, smile2.length);

		assertNotNull(citm.areaNames);
		assertEquals(17, citm.areaNames.size());
		assertNotNull(citm.events);
		assertEquals(184, citm.events.size());

		assertEquals(citm.seatCategoryNames.size(), citm2.seatCategoryNames.size());
		assertEquals(citm.subTopicNames.size(), citm2.subTopicNames.size());
		assertEquals(citm.subjectNames.size(), citm2.subjectNames.size());
		assertEquals(citm.topicNames.size(), citm2.topicNames.size());
		assertEquals(citm.topicSubTopics.size(), citm2.topicSubTopics.size());
		assertEquals(citm.venueNames.size(), citm2.venueNames.size());
	}
}
