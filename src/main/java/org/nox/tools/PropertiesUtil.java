package org.nox.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 配置文件读取工具类。
 * 从 classpath 下的 application.properties 中加载键值对，提供静态方法获取配置项。
 * 采用饿汉式单例模式，类加载时即完成配置读取。
 */
public class PropertiesUtil {
    /** 配置文件在 classpath 中的路径 */
    private static final String PROPERTIES_FILE = "/application.properties";
    /** 静态持有的 Properties 实例，类加载时初始化 */
    private static final Properties PROPERTIES = loadProperties();

    private PropertiesUtil() {
    }

    /**
     * 根据键获取配置值。
     * @param key 配置键名
     * @return 对应的配置值，如果不存在则返回 null
     */
    public static String get(String key) {
        return PROPERTIES.getProperty(key);
    }

    /**
     * 加载配置文件到 Properties 对象。
     * 使用 UTF-8 编码读取，支持中文内容。
     * @return 加载完成的 Properties 对象
     */
    private static Properties loadProperties() {
        try (InputStream inputStream = PropertiesUtil.class.getResourceAsStream(PROPERTIES_FILE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Properties file not found: " + PROPERTIES_FILE);
            }
            Properties properties = new Properties();
            // 使用 InputStreamReader 指定 UTF-8 编码，防止中文乱码
            properties.load(new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load properties file: " + PROPERTIES_FILE, e);
        }
    }
}
