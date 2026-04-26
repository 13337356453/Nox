package org.nox.controller;

import org.nox.tools.SQLiteUtil;
import org.nox.tools.SQLiteUtil.DataRecord;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 连接数据管理控制器。
 * 提供 RESTful API 对 SQLite 中的连接记录进行增删改查操作。
 * 前端左侧连接列表的数据来源。
 */
@RestController
@RequestMapping("/api/data")
public class DataController {

    /**
     * 获取所有连接记录。
     * @return 连接记录列表
     */
    @GetMapping
    public List<DataRecord> getAll() {
        return SQLiteUtil.getAll();
    }

    /**
     * 根据 ID 获取单条连接记录。
     * @param id 连接 ID
     * @return 对应的连接记录
     */
    @GetMapping("/{id}")
    public DataRecord get(@PathVariable int id) {
        return SQLiteUtil.get(id);
    }

    /**
     * 新增一条连接记录。
     * @param body 请求体，包含 url 字段
     * @return 操作结果，success 表示是否成功
     */
    @PostMapping
    public Map<String, Object> add(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        int rows = SQLiteUtil.add(url);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("success", rows > 0);
        return result;
    }

    /**
     * 修改指定 ID 的连接记录。
     * @param id   连接 ID
     * @param body 请求体，包含新的 url 字段
     * @return 操作结果
     */
    @PutMapping("/{id}")
    public Map<String, Object> edit(@PathVariable int id, @RequestBody Map<String, String> body) {
        String url = body.get("url");
        int rows = SQLiteUtil.edit(id, url);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("success", rows > 0);
        return result;
    }

    /**
     * 删除指定 ID 的连接记录。
     * @param id 连接 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable int id) {
        int rows = SQLiteUtil.del(id);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("success", rows > 0);
        return result;
    }
}
