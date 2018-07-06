package org.json.util.generic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.json.JSONObject;

public class JSONStringUtility {
	/**
	 * <pre>
	 * Replaces all hash tags within a given String with the values of a given JSON
	 * object using the object's keys as the tag-names.
	 * Hash tags must be in the for #TAG_NAME# (e.g. #my_hash_tag#)
	 * 
	 * <b>Example:</b>
	 * String my_tagged_string = "I would just like to say.. #tag_1# #tag_2# !!!";
	 * JSONObject my_tag_values = new JSONObject();
	 * my_tag_values.put("tag_1", "Hello");
	 * my_tag_values.put("tag_2", "world");
	 * System.out.println(JSONStringUtility.updateHashTags(my_tagged_string, my_tag_values));
	 * 
	 * <b>Output:</b>
	 * I would just like to say.. Hello world !!!
	 * </pre>
	 * @param input_string : the string that contain the hash tags
	 * @param tag_values : the JSON object containing the hash tag values to be applied (key/value = tag-name/replacement-value)
	 * @return Returns the updated String
	 */
	public static String updateHashTags(String input_string, JSONObject tag_values) {
		String output = input_string;
		for(String tag : tag_values.keySet()){
			 output = output.replace("#" + tag + "#", tag_values.getString(tag));
		}
		return output;
	}
		
	/**
	 * <pre>
	 * Replaces all hash tags within a given text InputStream with the values of a given JSON
	 * object using the object's keys as the tag-names.
	 * Hash tags must be in the for #TAG_NAME# (e.g. #my_hash_tag#)
	 * 
	 * <b>Example:</b>
	 * <i>
	 * Prerequisite : A text resource file (e.g. 'my.resources.MyResourceFile.txt')
	 * with the text 'I would just like to say.. #tag_1# #tag_2# !!!' as the file contents.
	 * </i>
	 * 
	 * JSONObject my_tag_values = new JSONObject();
	 * my_tag_values.put("tag_1", "Hello");
	 * my_tag_values.put("tag_2", "world");
	 * System.out.println(JSONStringUtility.updateHashTags(getClass().getResourceAsStream("/my/resources/MyResourceFile.txt"), my_tag_values));
	 * 
	 * <b>Output:</b>
	 * I would just like to say.. Hello world !!!
	 * </pre>
	 * @param input_stream : the text input stream that contains the tags
	 * @param tag_values : the JSON object containing the hash tag values to be applied (key/value = tag-name/replacement-value)
	 * @return Returns the updated String
	 */
	public static String updtateHashTags(InputStream input_stream, JSONObject tag_values) {
		return updateHashTags(getStringFromInputStream(input_stream), tag_values);
	}
	
	/**
	 * <pre>
	 * Replaces all tags within a given String with the values of a given JSON
	 * object using the object's keys as the tag-names.
	 * Tags must be in the form of TAG_PREFIX.TAG_NAME.TAG_SUFFIX 
	 * (e.g. if tag_prefix="{@literal <TAG>}" and tag_suffix="{@literal </TAG>}" the the tag is {@literal <TAG>}my_tag{@literal </TAG>})
	 * 
	 * <b>Example:</b>
	 * String my_tagged_string = "I would just like to say.. #tag_1# #tag_2# !!!";
	 * JSONObject my_tag_values = new JSONObject();
	 * my_tag_values.put("tag_1", "Hello");
	 * my_tag_values.put("tag_2", "world");
	 * System.out.println(JSONStringUtility.updateStringTags(my_tagged_string, "{@literal <TAG>}", "{@literal </TAG>}", my_tag_values));
	 * <b>Output:</b>
	 * I would just like to say.. Hello world !!!
	 * <pre>
	 * @param input_string : the string that contains the tags
	 * @param tag_prefix : the prefix tag
	 * @param tag_suffix : the suffix tag
	 * @param tag_values : the JSON object containing the tag values to be applied (key/value = tag-name/replacement-value)
	 * @return Returns the updated String
	 */
	public static String updateStringTags(String input_string, String tag_prefix, String tag_suffix, JSONObject tag_values) {
		String output = input_string;
		for(String tag_name : tag_values.keySet()){
			 output = output.replace(tag_prefix + tag_name + tag_suffix, tag_values.getString(tag_name));
		}
		return output;
	}
	
	/**
	 * <pre>
	 * Replaces all tags within a given String with the values of a given JSON
	 * object using the object's keys as the tag-names.
	 * Tags must be in the form of TAG_PREFIX.TAG_NAME.TAG_SUFFIX 
	 * (e.g. if tag_prefix="{@literal <TAG>}" and tag_suffix="{@literal </TAG>}" the the tag is {@literal <TAG>}my_tag{@literal </TAG>})
	 * 
	 * <b>Example:</b>
	 * String my_tagged_string = "I would just like to say.. #tag_1# #tag_2# !!!";
	 * JSONObject my_tag_values = new JSONObject();
	 * my_tag_values.put("tag_1", "Hello");
	 * my_tag_values.put("tag_2", "world");
	 * System.out.println(JSONStringUtility.updateStringTags(my_tagged_string, "{@literal <TAG>}", "{@literal </TAG>}", my_tag_values));
	 * <b>Output:</b>
	 * I would just like to say.. Hello world !!!
	 * <pre>
	 * @param input_stream : the text input stream that contains the tags
	 * @param tag_prefix : the prefix tag
	 * @param tag_suffix : the suffix tag
	 * @param tag_values : the JSON object containing the tag values to be applied (key/value = tag-name/replacement-value)
	 * @return Returns the updated String
	 */
	public static String updateStringTags(InputStream input_stream, String tag_prefix, String tag_suffix, JSONObject tag_values) {
		return updateStringTags(getStringFromInputStream(input_stream), tag_prefix, tag_suffix, tag_values);
	}
	
	
	/**
	 * <pre>
	 * Returns the contents a given text InputStream as a String
	 * 
	 * <b>Example:</b>
	 * <i>
	 * Prerequisite : A text resource file (e.g. 'my.resources.MyResourceFile.txt')
	 * with the text 'Hello world !!!' as the file contents.
	 * </i>
	 * 
	 * System.out.println(JSONStringUtility.getStringFromInputStream(getClass().getResourceAsStream("/my/resources/MyResourceFile.txt")));
	 * 
	 * <b>Output:</b>
	 * Hello world !!!
	 * </pre>
	 * @param input_stream : A text InputStream 
	 * @return Returns the InputStream contents as String
	 */
	public static String getStringFromInputStream(InputStream input_stream) {
		BufferedReader buffered_reader = null;
		StringBuilder string_builder = new StringBuilder();
		String line;
		try {
			buffered_reader = new BufferedReader(new InputStreamReader(input_stream));
			while ((line = buffered_reader.readLine()) != null) {
				string_builder.append(line);
			}
		} catch (IOException exception) {
			exception.printStackTrace();
		} finally {
			if (buffered_reader != null) {
				try {
					buffered_reader.close();
				} catch (IOException exception) {
					exception.printStackTrace();
				}
			}
		}
		return string_builder.toString();
	}
	
	
	/**
	 * <pre>
	 * Returns the contents a given text resource as a String
	 * 
	 * <b>Example:</b>
	 * <i>
	 * Prerequisite : A text resource file (e.g. 'my.resources.MyResourceFile.txt')
	 * with the text 'Hello world !!!' as the file contents.
	 * </i>
	 * 
	 * System.out.println(JSONStringUtility.getStringFromResource("/my/resources/MyResourceFile.txt"));
	 * 
	 * <b>Output:</b>
	 * Hello world !!!
	 * </pre>
	 * @param resource_path
	 * @return Returns the resource contents as String
	 */
	public static String getStringFromResource(String resource_path) {
		return getStringFromInputStream(JSONStringUtility.class.getResourceAsStream(resource_path));
	}
	
}
