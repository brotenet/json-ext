package org.json.util.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.json.JSONArray;
import org.json.exceptions.JSONException;
import org.json.util.converters.SQLConverter;

public class JDBCClient {
	
	
	/**
	 * Run an SQLConverter query and get the result as a JSONArray.
	 * 
	 * @param connection
	 * @param sql
	 * @return Returns a JSONArray of JSONObjects as rows having the column name as JSON key and the cell value as JSON value
	 * @throws JSONException
	 * @throws SQLException
	 */
	public static JSONArray query(Connection connection, String sql) throws JSONException, SQLException {
		return SQLConverter.convert(connection.createStatement().executeQuery(sql));
	}
	
	/**
	 * Run an SQLConverter query and get the result as a JSONArray.
	 * 
	 * @param jdbc_driver
	 * @param url
	 * @param user
	 * @param password
	 * @param sql
	 * @return Returns a JSONArray of JSONObjects as rows having the column name as JSON key and the cell value as JSON value
	 * @throws ClassNotFoundException
	 * @throws SQLException
	 */
	public static JSONArray query(String jdbc_driver, String url, String user, String password, String sql) throws ClassNotFoundException, SQLException {
		return query(getJDBCConnection(jdbc_driver, url, user, password), sql);
	}
	
	/**
	 * Run an SQLConverter query and get the result as a JSONArray.
	 * 
	 * @param jdbc_driver
	 * @param url
	 * @param user
	 * @param password
	 * @param sql
	 * @return Returns a JSONArray of JSONObjects as rows having the column name as JSON key and the cell value as JSON value
	 * @throws ClassNotFoundException
	 * @throws SQLException
	 */
	public static JSONArray query(Class<?> jdbc_driver, String url, String user, String password, String sql) throws ClassNotFoundException, SQLException {
		return query(getJDBCConnection(jdbc_driver.getName(), url, user, password), sql);
	}
	
	/**
	 * Run an SQLConverter statement and get success state as boolean.
	 * 
	 * @param connection
	 * @param sql
	 * @return Returns 'true' if execution was successful or 'false' if unsuccessful
	 * @throws SQLException
	 */
	public static boolean execute(Connection connection, String sql) throws SQLException {
		return connection.createStatement().execute(sql);
	}
	
	/**
	 * Run an SQLConverter statement and get success state as boolean.
	 * 
	 * @param jdbc_driver
	 * @param url
	 * @param user
	 * @param password
	 * @param sql
	 * @return Returns 'true' if execution was successful or 'false' if unsuccessful
	 * @throws SQLException
	 */
	public static boolean execute(String jdbc_driver, String url, String user, String password, String sql) throws ClassNotFoundException, SQLException {
		return execute(getJDBCConnection(jdbc_driver, url, user, password), sql);
	}
	
	/**
	 * Run an SQLConverter statement and get success state as boolean.
	 * 
	 * @param jdbc_driver
	 * @param url
	 * @param user
	 * @param password
	 * @param sql
	 * @return Returns 'true' if execution was successful or 'false' if unsuccessful
	 * @throws SQLException
	 */
	public static boolean execute(Class<?> jdbc_driver, String url, String user, String password, String sql) throws ClassNotFoundException, SQLException {
		return execute(getJDBCConnection(jdbc_driver.getName(), url, user, password), sql);
	}
	
	/**
	 * Create a JDBC connection from a given JDBC driver class-name, a connection-string URL, a user-name and a password. 
	 * 
	 * @param jdbc_driver
	 * @param url
	 * @param user
	 * @param password
	 * @return Returns the JDBC connection
	 * @throws ClassNotFoundException
	 * @throws SQLException
	 */
	public static Connection getJDBCConnection(String jdbc_driver, String url, String user, String password) throws ClassNotFoundException, SQLException {
		Class.forName(jdbc_driver);
		return DriverManager.getConnection(url, user, password);
	}

}
