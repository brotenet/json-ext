package org.json.util.converters;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.internal.pojo.JSONPOJOReader;
import org.json.internal.pojo.JSONPOJOWriter;

public class POJOConverter {
	
	public static String toJSON(Object pojo) {
		Map<String, Object> options = new HashMap<String, Object>();
		options.put(JSONPOJOWriter.DATE_FORMAT, "yyyy/MM/dd HH:mm");
		options.put(JSONPOJOWriter.PRETTY_PRINT, true);
		options.put(JSONPOJOWriter.WRITE_LONGS_AS_STRINGS, true);
		options.put(JSONPOJOWriter.SKIP_NULL_FIELDS, false);
		return JSONPOJOWriter.objectToJson(pojo, options);
	}
		
	public static String toJSON(Object pojo, String date_format, boolean longs_as_strings, boolean skip_nulls, boolean prettify) {
		Map<String, Object> options = new HashMap<String, Object>();
		options.put(JSONPOJOWriter.DATE_FORMAT, date_format);
		options.put(JSONPOJOWriter.PRETTY_PRINT, prettify);
		options.put(JSONPOJOWriter.WRITE_LONGS_AS_STRINGS, longs_as_strings);
		options.put(JSONPOJOWriter.SKIP_NULL_FIELDS, skip_nulls);
		return JSONPOJOWriter.objectToJson(pojo, options);
	}
	
	public static JSONObject toJSONObject(Object pojo) {
		return new JSONObject(toJSON(pojo));
	}
	
	public static JSONObject toJSONObject(Object pojo, String date_format, boolean longs_as_strings, boolean skip_nulls) {
		return new JSONObject(toJSON(pojo, date_format, longs_as_strings, skip_nulls, false));
	}
	
	public static JSONArray toJSONArray(Object[] pojos) {
		JSONArray output = new JSONArray();
		for(Object pojo : pojos) {
			output.put(toJSONObject(pojo));
		}		
		return output;
	}
	
	public static JSONArray toJSONArray(Object[] pojos, String date_format, boolean longs_as_strings, boolean skip_nulls) {
		JSONArray output = new JSONArray();
		for(Object pojo : pojos) {
			output.put(toJSONObject(pojo, date_format, longs_as_strings, skip_nulls));
		}		
		return output;
	}
	
	public static Object toObject(String json, boolean use_maps) {
		if(use_maps == true) {
			Map<String, Object> options = new HashMap<String, Object>();
			options.put(JSONPOJOReader.USE_MAPS, true);
			return JSONPOJOReader.jsonToJava(json, options);
		}else {
			return JSONPOJOReader.jsonToJava(json);
		}
	}
	
	public static Object toObject(String json) {
		return JSONPOJOReader.jsonToJava(json);
	}
	
	public static Object toObject(InputStream json, boolean use_maps) {
		if(use_maps == true) {
			Map<String, Object> options = new HashMap<String, Object>();
			options.put(JSONPOJOReader.USE_MAPS, true);
			return JSONPOJOReader.jsonToJava(json, options);
		}else {
			return JSONPOJOReader.jsonToJava(json, new HashMap<>());
		}
	}
	
	public static Object toObject(InputStream json) {
		return JSONPOJOReader.jsonToJava(json, new HashMap<>());
	}
}
