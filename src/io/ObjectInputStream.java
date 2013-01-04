package io;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamClass;
/**
 * An ObjectInputStream where you can specify the ClassLoader to use. 
 * If the specified ClassLoader throws an Exception, the standard ClassLoader is used instead. 
 * 
 * @author Erik Krogh Kristensen
 *
 */
public class ObjectInputStream extends java.io.ObjectInputStream {
	@Override
	public Class<?> resolveClass(ObjectStreamClass desc) throws IOException,ClassNotFoundException {
		try {
			// First we see if our classloader can handle it. 
			return loader.loadClass(desc.getName());
		} catch (Exception e) {
			// Nothing
		}
		return super.resolveClass(desc);
	}

	ClassLoader loader;
	public ObjectInputStream(InputStream in, ClassLoader loader) throws IOException {
		super(in);
		this.loader = loader;
	}

}