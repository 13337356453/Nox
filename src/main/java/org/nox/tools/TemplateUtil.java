package org.nox.tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 模板读取工具类。
 * 从 classpath 中读取 Java 模板文件（如 Info.txt、RCE.txt），
 * 返回字符串内容供 PayloadGenerater 使用。
 */
public class TemplateUtil {
    private TemplateUtil() {
    }

    /**
     * 读取 classpath 下的模板文件。
     * @param resourcePath 资源路径，如 "/JavaTemplates/RCE.txt"
     * @return 模板文件内容的 UTF-8 字符串
     */
    public static String readTemplate(String resourcePath) {
        try (InputStream inputStream = TemplateUtil.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Template not found: " + resourcePath);
            }
            // JDK 8 兼容方式：使用 ByteArrayOutputStream 循环读取全部字节
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read template: " + resourcePath, e);
        }
    }
}
