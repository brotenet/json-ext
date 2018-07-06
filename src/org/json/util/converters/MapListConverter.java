package org.json.util.converters;

import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONObject;

public class MapListConverter {

	/**
	 * Convert a JSONObject to a HashMap. Any contained JSONArray elements are converted to ArrayList
	 * @param json_object
	 * @return HashMap<key:String, value:Object>
	 */
	public static HashMap<String, Object> toHashmap(JSONObject json_object) {
		HashMap<String, Object> output = new HashMap<String, Object>();
		for(String key : json_object.keySet()) {
			Object element = json_object.get(key);
			if(element instanceof JSONObject) {
				output.put(key, toHashmap((JSONObject) element));
			}else if(element instanceof JSONArray) {
				output.put(key, toArrayList((JSONArray) element));
			}else {
				output.put(key, element);
			}
		}
		return output;
	}
	
	/**
	 * Convert a JSONArray to a ArrayList. Any contained JSONObject elements are converted to HashMap
	 * @param json_array
	 * @return ArrayList<Object>
	 */
	public static ArrayList<Object> toArrayList(JSONArray json_array) {
		ArrayList<Object> output = new ArrayList<Object>();
		for(Object element : json_array.toArray()) {
			if(element instanceof JSONObject) {
				output.add(toHashmap((JSONObject) element));
			}else if(element instanceof JSONArray) {
				output.add(toArrayList((JSONArray) element));
			}else {
				output.add(element);
			}
		}
		return output;
	}
	
}
