package io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
/**
 * This class has static functions that helps converting a class to an byte array. 
 * 
 * @author Erik Krogh Kristensen
 *
 */
public class Classes {
	public static Map<String, byte[]> toClassNameAndBytesMap(Class<?>... classes) throws IOException {
		Map<String, byte[]> classesList = new HashMap<String, byte[]>();
		// One class at a time.
		for (Class<?> clazz : classes) {
			classesList.put(clazz.getName(), getClassBytes(clazz));
		}
		return classesList;
	}
	public static byte[] getClassBytes(Class<?> clazz) throws IOException {
		String className = clazz.getName();
		String classAsPath = className.replace('.', '/') + ".class";
		if (clazz.getClassLoader() == null) {
			System.out.println("Null:" + clazz);
		}
		InputStream stream = clazz.getClassLoader().getResourceAsStream(classAsPath);

		return inputStreamToBytes(stream);
	}
	private static byte[] inputStreamToBytes(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		int nRead;
		byte[] data = new byte[16384];

		while ((nRead = is.read(data, 0, data.length)) != -1) {
			buffer.write(data, 0, nRead);
		}

		buffer.flush();

		return buffer.toByteArray();
	}
}
