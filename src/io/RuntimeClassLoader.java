package io;

import java.util.Map;
/**
 * 
 * 
 * @author Erik Krogh Kristensen
 *
 */
public class RuntimeClassLoader extends ClassLoader {
	Map<String, byte[]> classes;
	public RuntimeClassLoader(Map<String, byte[]> classes) {
		super(RuntimeClassLoader.class.getClassLoader());
		this.classes = classes;
	}

	public void setClassesMap(Map<String, byte[]> classes) {
		this.classes = classes;
	}

	@Override
	public Class<?> loadClass(String s) {
		return findClass(s);
	}
	@Override
	public Class<?> findClass(String s) {
		try {
			return super.loadClass(s);
		} catch (Throwable exception) {
			System.out.println(classes);
			byte[] bytes = classes.get(s);
			if (bytes != null)
			{
				return defineClass(s, bytes, 0, bytes.length);
			}
		}
		return null;
	}
}