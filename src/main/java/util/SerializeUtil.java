package util;

import java.io.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            byte[] bytes = baos.toByteArray();
            return bytes;
        } catch (Exception e) {
            LOGGER.error("Exception", e);
        } finally {
            try {
                oos.close();
            } catch (IOException e) {
                LOGGER.error("Exception", e);
            }
            try {
                baos.close();
            } catch (IOException e) {
                LOGGER.error("Exception", e);
            }
        }
        return null;
    }

    public static Object unserialize(byte[] bytes) {

        ByteArrayInputStream bais = null;
        ObjectInputStream ois = null;
        try {

            bais = new ByteArrayInputStream(bytes);
            ois = new ObjectInputStream(bais);
            return ois.readObject();
        } catch (Exception e) {
            LOGGER.error("Exception", e);
        } finally {

            try {
                ois.close();
            } catch (IOException e) {
                LOGGER.error("Exception", e);
            }
            try {
                bais.close();
            } catch (IOException e) {
                LOGGER.error("Exception", e);
            }
        }

        return null;

    }

}
