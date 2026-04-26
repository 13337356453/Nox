package org.nox.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 请求工具类（JDK 8 兼容版）。
 * 基于 HttpURLConnection 实现，提供 GET/POST 等常用请求方法。
 * 所有方法均为静态，无需实例化。
 */
public final class HttpRequestUtil {
    /** 连接超时时间（毫秒） */
    private static final int CONNECT_TIMEOUT = 10000;
    /** 读取超时时间（毫秒） */
    private static final int READ_TIMEOUT = 20000;

    private HttpRequestUtil() {
    }

    /**
     * 发送 GET 请求。
     * @param url 目标地址
     * @return 响应对象
     */
    public static Response get(String url) {
        return request("GET", url, null, null);
    }

    /**
     * 发送带请求头的 GET 请求。
     * @param url     目标地址
     * @param headers 请求头键值对
     * @return 响应对象
     */
    public static Response get(String url, Map<String, String> headers) {
        return request("GET", url, headers, null);
    }

    /**
     * 发送 POST 请求。
     * @param url  目标地址
     * @param body 请求体
     * @return 响应对象
     */
    public static Response post(String url, String body) {
        return request("POST", url, null, body);
    }

    /**
     * 发送带请求头的 POST 请求。
     * @param url     目标地址
     * @param headers 请求头键值对
     * @param body    请求体
     * @return 响应对象
     */
    public static Response post(String url, Map<String, String> headers, String body) {
        return request("POST", url, headers, body);
    }

    /**
     * 通用 HTTP 请求方法。
     * @param method  请求方法（GET/POST 等）
     * @param url     目标地址
     * @param headers 请求头键值对
     * @param body    请求体（可为 null）
     * @return 响应对象
     */
    public static Response request(String method, String url, Map<String, String> headers, String body) {
        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod(method.toUpperCase());
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            // 允许自动跟随重定向
            connection.setInstanceFollowRedirects(true);

            // 安全处理请求头，防止空指针
            Map<String, String> safeHeaders = headers == null ? Collections.<String, String>emptyMap() : new HashMap<>(headers);
            for (Map.Entry<String, String> entry : safeHeaders.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            // 如果存在请求体，开启输出流并写入数据
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            // 获取响应状态码
            int statusCode = connection.getResponseCode();
            // 获取响应头
            Map<String, List<String>> responseHeaders = connection.getHeaderFields();
            // 读取响应体（优先从正常输入流读取，失败时尝试错误流）
            StringBuilder responseBody = new StringBuilder();
            BufferedReader reader;
            try {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                if (connection.getErrorStream() != null) {
                    reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8));
                } else {
                    throw e;
                }
            }
            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line).append("\n");
            }
            reader.close();

            return new Response(statusCode, responseHeaders, responseBody.toString());
        } catch (IOException e) {
            throw new RuntimeException("HTTP request failed", e);
        } finally {
            // 断开连接，释放资源
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * HTTP 响应封装类。
     * 包含状态码、响应头和响应体。
     */
    public static class Response {
        private final int statusCode;
        private final Map<String, List<String>> headers;
        private final String body;

        public Response(int statusCode, Map<String, List<String>> headers, String body) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
        }

        public int statusCode() {
            return statusCode;
        }

        public Map<String, List<String>> headers() {
            return headers;
        }

        public String body() {
            return body;
        }
    }

    /**
     * 本地测试入口。
     */
    public static void main(String[] args) {
        String url = "http://www.baidu.com";
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Usage-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36");
        Response response = get(url, headers);
        if (response.statusCode() == 200) {
            System.out.println(response.body());
        }
    }
}
