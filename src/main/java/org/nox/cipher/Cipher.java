package org.nox.cipher;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 加密与编码工具类。
 * 提供 Base64、AES(ECB/CBC)、异或(XOR)、GZip 压缩/解压以及随机字符串生成等功能。
 * 主要用于 Payload 的加密处理与结果解密。
 */
public class Cipher {

    /**
     * Base64 编解码（字节数组版本）。
     * @param data 待处理的数据
     * @param mode 0 为编码，1 为解码
     * @return 编码或解码后的字节数组
     */
    public static byte[] base64(byte[] data, int mode) {
        if (mode == 0) {
            return Base64.getEncoder().encode(data);
        } else {
            return Base64.getDecoder().decode(data);
        }
    }

    /**
     * Base64 编解码（字符串版本）。
     * @param data 待处理的字符串
     * @param mode 0 为编码，1 为解码
     * @return 编码或解码后的字符串
     */
    public static String base64(String data, int mode) {
        if (mode == 0) {
            return Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
        } else {
            return new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
        }
    }

    /**
     * AES ECB 模式加密/解密（字节数组版本）。
     * @param data 待处理数据
     * @param key  AES 密钥
     * @param mode 0 为加密，1 为解密
     * @return 加密或解密后的字节数组
     */
    public static byte[] aesECB(byte[] data, byte[] key, int mode) {
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
            cipher.init(mode == 0 ? javax.crypto.Cipher.ENCRYPT_MODE : javax.crypto.Cipher.DECRYPT_MODE, secretKeySpec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("AES ECB error", e);
        }
    }

    /**
     * AES ECB 模式加密/解密（字符串版本）。
     * 输入输出均为 Base64 编码的字符串，便于传输。
     * @param data 待处理字符串（Base64 格式时为解密，明文时为加密）
     * @param key  AES 密钥
     * @param mode 0 为加密，1 为解密
     * @return 处理后的字符串
     */
    public static String aesECB(String data, String key, int mode) {
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            if (mode == 0) {
                byte[] encrypted = aesECB(data.getBytes(StandardCharsets.UTF_8), keyBytes, 0);
                return Base64.getEncoder().encodeToString(encrypted);
            } else {
                byte[] decoded = Base64.getDecoder().decode(data);
                return new String(aesECB(decoded, keyBytes, 1), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * AES CBC 模式加密/解密（字节数组版本）。
     * @param data 待处理数据
     * @param key  AES 密钥
     * @param iv   初始化向量
     * @param mode 0 为加密，1 为解密
     * @return 加密或解密后的字节数组
     */
    public static byte[] aesCBC(byte[] data, byte[] key, byte[] iv, int mode) {
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(mode == 0 ? javax.crypto.Cipher.ENCRYPT_MODE : javax.crypto.Cipher.DECRYPT_MODE, secretKeySpec, ivSpec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("AES CBC error", e);
        }
    }

    /**
     * AES CBC 模式加密/解密（字符串版本）。
     * @param data 待处理字符串
     * @param key  AES 密钥
     * @param iv   初始化向量
     * @param mode 0 为加密，1 为解密
     * @return 处理后的字符串
     */
    public static String aesCBC(String data, String key, String iv, int mode) {
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] ivBytes = iv.getBytes(StandardCharsets.UTF_8);
            if (mode == 0) {
                byte[] encrypted = aesCBC(data.getBytes(StandardCharsets.UTF_8), keyBytes, ivBytes, 0);
                return Base64.getEncoder().encodeToString(encrypted);
            } else {
                byte[] decoded = Base64.getDecoder().decode(data);
                return new String(aesCBC(decoded, keyBytes, ivBytes, 1), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 异或逻辑运算。
     * 使用 key 循环对 data 进行逐字节异或。
     * @param data 待处理数据
     * @param key  异或密钥
     * @return 异或后的字节数组
     */
    public static byte[] xor(byte[] data, byte[] key) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return result;
    }

    /**
     * 生成指定长度的随机字符串。
     * 字符集包含大小写字母和数字。
     * @param length 字符串长度
     * @return 随机字符串
     */
    public static String generateRandom(int length) {
        String charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(charset.length());
            sb.append(charset.charAt(index));
        }
        return sb.toString();
    }

    /**
     * GZip 压缩。
     * @param data 待压缩的字节数组
     * @return 压缩后的字节数组
     */
    public static byte[] compress(byte[] data) {
        if (data == null || data.length == 0) return data;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data);
            gzip.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("GZip Compress Error", e);
        }
    }

    /**
     * GZip 解压。
     * @param data 待解压的字节数组
     * @return 解压后的字节数组
     */
    public static byte[] decompress(byte[] data) {
        if (data == null || data.length == 0) return data;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             GZIPInputStream gis = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("GZip Decompress Error", e);
        }
    }
}
