package xyz.lawlietcache.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class SerializeUtil {

    private final static Logger LOGGER = LoggerFactory.getLogger(SerializeUtil.class);

    private SerializeUtil() {
    }

    public static byte[] serialize(Object object) {
        ByteArrayOutputStream baos = null;
        ObjectOutputStream oos = null;
        try {
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(object);
            return baos.toByteArray();
        } catch (Exception e) {
            if (!(e instanceof EOFException)) {
                LOGGER.error("Exception", e);
            }
        } finally {
            try {
                if (oos != null) {
                    oos.close();
                }
            } catch (IOException e) {
                LOGGER.error("Exception", e);
            }
            try {
                if (baos != null) {
                    baos.close();
                }
            } catch (IOException e) {
                LOGGER.error("Exception", e);
            }
        }
        return null;
    }

    public static Object unserialize(byte[] bytes) {
        ByteArrayInputStream bais = null;
        CustomObjectInputStream ois = null;
        try {
            bais = new ByteArrayInputStream(bytes);
            ois = new CustomObjectInputStream(bais);
            return ois.readObject();
        } catch (Exception e) {
            if (!(e instanceof EOFException)) {
                LOGGER.error("Exception", e);
            }
        } finally {
            try {
                if (ois != null) {
                    ois.close();
                }
            } catch (IOException e) {
                LOGGER.error("Exception", e);
            }
            try {
                if (bais != null) {
                    bais.close();
                }
            } catch (IOException e) {
                LOGGER.error("Exception", e);
            }
        }

        return null;

    }


    private static class CustomObjectInputStream extends ObjectInputStream {

        public CustomObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String oldClassName = "core.HttpResponse";
            String newClassName = "xyz.lawlietcache.core.HttpResponse";

            if (desc.getName().equals(oldClassName)) {
                return Class.forName(newClassName);
            }
            return super.resolveClass(desc);
        }

    }

}
