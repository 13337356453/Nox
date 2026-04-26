package org.nox.tools;

import org.nox.cipher.Cipher;

/**
 * 响应结果解密工具类。
 * 目标返回的响应体采用 JWT 格式（三段点分隔），第二段为 AES-ECB 加密的内容。
 * 此类负责提取第二段并进行解密，还原出原始明文结果。
 */
public class ResultReader {
    /**
     * 解密目标返回的响应 Payload。
     * @param payload 目标返回的原始字符串（格式：header.payload.signature）
     * @return 解密后的明文结果
     */
    public static String read(String payload) {
        // 按点号分割，取第二段（索引为 1）进行 AES-ECB 解密
        return Cipher.aesECB(payload.split("\\.")[1], PropertiesUtil.get("aesKey"), 1);
    }
}
