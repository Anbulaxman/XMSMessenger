package com.alex.mms.com.android.mms;

import java.lang.reflect.Method;
import android.content.Context;

public class SystemPropertiesProxy
{
	/**
	 * This class cannot be instantiated
	 */
	private SystemPropertiesProxy()
	{

	}

	/**
	 * Get the value for the given key.
	 * 
	 * @return an empty string if the key isn't found
	 * @throws IllegalArgumentException
	 *             if the key exceeds 32 characters
	 */
	public static String get(Context context, String key) throws IllegalArgumentException
	{
		String ret = "";

		try
		{
			ClassLoader cl = context.getClassLoader();
			@SuppressWarnings("rawtypes")
			Class SystemProperties = cl.loadClass("android.os.SystemProperties");

			// Parameters Types
			@SuppressWarnings("rawtypes")
			Class[] paramTypes = new Class[1];
			paramTypes[0] = String.class;

			Method get = SystemProperties.getMethod("get", paramTypes);

			// Parameters
			Object[] params = new Object[1];
			params[0] = new String(key);

			ret = (String) get.invoke(SystemProperties, params);
		}
		catch (IllegalArgumentException iAE)
		{
			throw iAE;
		}
		catch (Exception e)
		{
			ret = "";
			// TODO
		}

		return ret;
	}

	/**
	 * Get the value for the given key.
	 * 
	 * @return if the key isn't found, return def if it isn't null, or an empty
	 *         string otherwise
	 * @throws IllegalArgumentException
	 *             if the key exceeds 32 characters
	 */
	public static String get(Context context, String key, String def) throws IllegalArgumentException
	{
		String ret = def;

		try
		{
			ClassLoader cl = context.getClassLoader();
			@SuppressWarnings("rawtypes")
			Class SystemProperties = cl.loadClass("android.os.SystemProperties");

			// Parameters Types
			@SuppressWarnings("rawtypes")
			Class[] paramTypes = new Class[2];
			paramTypes[0] = String.class;
			paramTypes[1] = String.class;

			Method get = SystemProperties.getMethod("get", paramTypes);

			// Parameters
			Object[] params = new Object[2];
			params[0] = new String(key);
			params[1] = new String(def);

			ret = (String) get.invoke(SystemProperties, params);
		}
		catch (IllegalArgumentException iAE)
		{
			throw iAE;
		}
		catch (Exception e)
		{
			ret = def;
			// TODO
		}

		return ret;
	}

	/**
	 * Get the value for the given key, and return as an integer.
	 * 
	 * @param key
	 *            the key to lookup
	 * @param def
	 *            a default value to return
	 * @return the key parsed as an integer, or def if the key isn't found or
	 *         cannot be parsed
	 * @throws IllegalArgumentException
	 *             if the key exceeds 32 characters
	 */
	public static Integer getInt(Context context, String key, int def) throws IllegalArgumentException
	{
		Integer ret = def;

		try
		{
			ClassLoader cl = context.getClassLoader();
			@SuppressWarnings("rawtypes")
			Class SystemProperties = cl.loadClass("android.os.SystemProperties");

			// Parameters Types
			@SuppressWarnings("rawtypes")
			Class[] paramTypes = new Class[2];
			paramTypes[0] = String.class;
			paramTypes[1] = int.class;

			Method getInt = SystemProperties.getMethod("getInt", paramTypes);

			// Parameters
			Object[] params = new Object[2];
			params[0] = new String(key);
			params[1] = new Integer(def);

			ret = (Integer) getInt.invoke(SystemProperties, params);
		}
		catch (IllegalArgumentException iAE)
		{
			throw iAE;
		}
		catch (Exception e)
		{
			ret = def;
			// TODO
		}

		return ret;
	}

	/**
	 * Get the value for the given key, and return as a long.
	 * 
	 * @param key
	 *            the key to lookup
	 * @param def
	 *            a default value to return
	 * @return the key parsed as a long, or def if the key isn't found or cannot
	 *         be parsed
	 * @throws IllegalArgumentException
	 *             if the key exceeds 32 characters
	 */
	public static Long getLong(Context context, String key, long def) throws IllegalArgumentException
	{
		Long ret = def;

		try
		{

			ClassLoader cl = context.getClassLoader();
			@SuppressWarnings("rawtypes")
			Class SystemProperties = cl.loadClass("android.os.SystemProperties");

			// Parameters Types
			@SuppressWarnings("rawtypes")
			Class[] paramTypes = new Class[2];
			paramTypes[0] = String.class;
			paramTypes[1] = long.class;

			Method getLong = SystemProperties.getMethod("getLong", paramTypes);

			// Parameters
			Object[] params = new Object[2];
			params[0] = new String(key);
			params[1] = new Long(def);

			ret = (Long) getLong.invoke(SystemProperties, params);
		}
		catch (IllegalArgumentException iAE)
		{
			throw iAE;
		}
		catch (Exception e)
		{
			ret = def;
			// TODO
		}

		return ret;
	}

	/**
	 * Get the value for the given key, returned as a boolean. Values 'n', 'no',
	 * '0', 'false' or 'off' are considered false. Values 'y', 'yes', '1',
	 * 'true' or 'on' are considered true. (case insensitive). If the key does
	 * not exist, or has any other value, then the default result is returned.
	 * 
	 * @param key
	 *            the key to lookup
	 * @param def
	 *            a default value to return
	 * @return the key parsed as a boolean, or def if the key isn't found or is
	 *         not able to be parsed as a boolean.
	 * @throws IllegalArgumentException
	 *             if the key exceeds 32 characters
	 */
	public static Boolean getBoolean(Context context, String key, boolean def) throws IllegalArgumentException
	{
		Boolean ret = def;

		try
		{
			ClassLoader cl = context.getClassLoader();
			@SuppressWarnings("rawtypes")
			Class SystemProperties = cl.loadClass("android.os.SystemProperties");

			// Parameters Types
			@SuppressWarnings("rawtypes")
			Class[] paramTypes = new Class[2];
			paramTypes[0] = String.class;
			paramTypes[1] = boolean.class;

			Method getBoolean = SystemProperties.getMethod("getBoolean", paramTypes);

			// Parameters
			Object[] params = new Object[2];
			params[0] = new String(key);
			params[1] = new Boolean(def);

			ret = (Boolean) getBoolean.invoke(SystemProperties, params);
		}
		catch (IllegalArgumentException iAE)
		{
			throw iAE;
		}
		catch (Exception e)
		{
			ret = def;
			// TODO
		}

		return ret;
	}

}