# Nox

> **⚠️ 安全声明：本项目仅为网络安全研究与教育目的设计，严禁用于任何未经授权的渗透测试、攻击或非法行为。使用者的任何违法行为与开发者无关，由使用者自行承担全部法律责任。**

Nox 是一个用于 **授权安全研究** 的 Java Webshell 管理控制端，旨在帮助安全研究人员、渗透测试工程师和红队成员在 **合法授权** 的环境下，深入理解 Web 后门的通信原理、动态类加载机制以及多层加密 Payload 的构建与传输过程。

本项目采用 Spring Boot 构建控制端，搭配轻量级 JSP 后门，通过自定义加密协议实现控制端与目标端的隐蔽通信。

---

## 📋 目录

- [安全声明与免责声明](#️-安全声明与免责声明)
- [功能特性](#-功能特性)
- [技术架构](#-技术架构)
- [快速开始](#-快速开始)
- [使用说明](#-使用说明)
- [通信协议](#-通信协议)
- [项目结构](#-项目结构)
- [配置说明](#-配置说明)
- [截图预览](#-截图预览)
- [许可证](#-许可证)

---

## ⚠️ 安全声明与免责声明

**请务必仔细阅读以下条款：**

1. **研究目的唯一性**：本项目仅用于 **网络安全技术研究、教育和漏洞演练**。任何使用本工具进行的未经授权的访问、数据窃取、系统破坏或其他违法行为，均与开发者无关。

2. **合法授权前提**：在使用本工具之前，您必须确保已获得目标系统的 **明确书面授权**。未授权的使用行为在大多数国家和地区属于刑事犯罪。

3. **责任自负原则**：开发者不对因使用或滥用本工具而导致的任何直接或间接损失、法律责任或损害承担责任。所有风险和使用后果由使用者自行承担。

4. **合规使用**：使用者有责任遵守所在国家/地区的所有适用法律法规，包括但不限于《中华人民共和国网络安全法》、《计算机欺诈和滥用法》(CFAA) 等。

5. **禁止恶意传播**：严禁将本项目用于制作、传播恶意软件，或用于任何危害网络安全的活动。

**如果您不同意以上任何条款，请立即停止使用并删除本项目。**

---

## ✨ 功能特性

- **🔍 Information Collection**：自动收集目标系统的系统信息（操作系统、Java 版本、网络配置、内存/CPU、环境变量、文件写权限等）
- **💻 Code Execution**：远程命令执行终端，支持 Windows / Linux 双平台自适应，前端维护工作目录状态
- **📁 File Manager**：远程文件浏览、上传、下载、删除、重命名、目录创建及在线文本编辑
- **🔐 多层加密通信**：GZip → XOR → AES-CBC 加密，请求体伪装为 JWT 格式传输
- **🧬 动态类加载**：目标端内存中解密并动态加载执行 Java 字节码，无落地文件
- **🔗 连接管理**：基于 SQLite 的本地连接记录管理，支持增删改查

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                        控制端 (Nox)                          │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │  Web 面板   │  │  Payload 生成 │  │  加密/编码/压缩引擎  │ │
│  │ (HTML/JS)   │◄─┤  (模板+编译)  │◄─┤ (GZip+XOR+AES-CBC) │ │
│  └──────┬──────┘  └──────────────┘  └─────────────────────┘ │
│         │                                                   │
│  ┌──────┴──────────────────────────────────────────────┐   │
│  │              Spring Boot REST API                    │   │
│  │  /api/data  /getinfo  /exec  /file                   │   │
│  └──────────────────────────────────────────────────────┘   │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP POST (JWT 格式加密 Payload)
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                        目标端 (shell.jsp)                    │
│  ┌──────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ 请求头认证    │  │ JWT 解析     │  │ AES-CBC + XOR + GZip│ │
│  │ & 字节码提取  │──┤ & Base64解码 │──┤ 解密 & 解压         │ │
│  └──────────────┘  └─────────────┘  └─────────────────────┘ │
│                                      │                      │
│  ┌───────────────────────────────────┘                      │
│  │ 反射调用 ClassLoader.defineClass() 内存加载并执行         │
│  └──────────────────────────────────────────────────────────┘
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP Response (JWT 格式加密结果)
                            ▼
```

### 核心流程

1. **模板编译**：控制端读取 Java 源码模板（Info / RCE / FileManager），注入用户命令后调用系统 `javac` 编译为 `.class` 字节码
2. **多层加密**：字节码经过 **GZip 压缩 → XOR 异或混淆 → AES-CBC 加密 → Base64 编码**
3. **JWT 伪装**：将加密后的 Payload 包装为 JWT 格式发送（`token=header.payload.signature`）
4. **内存执行**：目标端解密后，通过反射调用 `ClassLoader.defineClass()` 在内存中加载类并执行
5. **结果回传**：目标端将执行结果 AES-ECB 加密后，同样以 JWT 格式返回

---

## 🚀 快速开始

### 环境要求

- JDK 1.8+
- Maven 3.6+
- 目标服务器需部署 JSP 运行环境（Tomcat 等）

### 1. 克隆项目

```bash
git clone https://github.com/13337356453/Nox.git
cd Nox
```

### 2. 构建项目

```bash
mvn clean package
```

### 3. 启动控制端

```bash
java -jar target/Nox-1.0.0.jar
```

或直接运行主类：

```bash
mvn spring-boot:run
```

控制端默认运行在 `http://127.0.0.1:6789`

### 4. 部署目标端

将 `server/shell.jsp` 部署到目标服务器的 Web 目录中（需获得合法授权）。

---

## 📖 使用说明

### 1. 添加连接

打开控制端 Web 面板 (`http://127.0.0.1:6789`)，点击左侧 **Add** 按钮，输入目标 Webshell 的完整 URL（例如：`http://target.com/shell.jsp`）。

### 2. 信息收集

双击连接项，自动跳转到 **Information Collection** 页面，获取目标系统的详细信息。

### 3. 命令执行

切换到 **Code Execution** 标签页：
- 在输入框中输入命令，按 `Enter` 执行
- 支持 `cd` 切换目录（前端本地维护路径状态）
- 支持 `clear` 清空终端
- 使用上下方向键浏览命令历史

### 4. 文件管理

切换到 **File Manager** 标签页：
- 双击目录进入，点击 **↑ Parent** 返回上级
- 点击 **View** 在线编辑文本文件
- 点击 **Upload** 上传本地文件
- 点击 **Download** 下载远程文件
- 点击 **Delete** 删除文件或目录

---

## 🔑 通信协议

### 请求加密链

```
Java 源码
  → javac 编译 → .class 字节码
  → GZip 压缩
  → XOR 异或 (密钥: GRzGRYx7mRlIQgFd)
  → AES-CBC 加密 (密钥: VcApSBJ570GQhjrK, IV: PKAXoyeRITP9JfbF)
  → Base64 编码
  → JWT 格式包装
```

### 响应加密链

```
明文结果
  → AES-ECB 加密 (密钥: VcApSBJ570GQhjrK)
  → Base64 编码
  → JWT 格式包装
```

### 认证头

所有请求必须携带自定义 HTTP 头部进行认证：

```http
e10adc3949ba59ab: be56e057f20f883e
```

---

## 📁 项目结构

```
Nox/
├── server/
│   └── shell.jsp                 # 目标端 JSP Webshell
├── src/
│   ├── main/
│   │   ├── java/org/nox/
│   │   │   ├── NoxApplication.java               # Spring Boot 入口
│   │   │   ├── cipher/Cipher.java                # 加密/编码工具 (Base64, AES, XOR, GZip)
│   │   │   ├── controller/
│   │   │   │   ├── DataController.java           # 连接管理 CRUD API
│   │   │   │   ├── InfoController.java           # 信息收集接口
│   │   │   │   ├── ExecController.java           # 命令执行接口
│   │   │   │   └── FileController.java           # 文件管理接口
│   │   │   └── tools/
│   │   │       ├── HttpRequestUtil.java          # HTTP 客户端
│   │   │       ├── PayloadGenerater.java         # Payload 生成器
│   │   │       ├── Transformer.java              # 编译+压缩+加密转换器
│   │   │       ├── ResultReader.java             # 响应结果解密器
│   │   │       ├── SQLiteUtil.java               # SQLite 数据库操作
│   │   │       ├── TemplateUtil.java             # 模板文件读取
│   │   │       └── PropertiesUtil.java           # 配置读取
│   │   └── resources/
│   │       ├── application.properties            # 配置文件 (端口/密钥)
│   │       ├── JavaTemplates/
│   │       │   ├── Info.txt                      # 信息收集类模板
│   │       │   ├── RCE.txt                       # 命令执行类模板
│   │       │   └── FileManager.txt               # 文件管理类模板
│   │       ├── static/
│   │       │   ├── index.html                    # Web 控制面板
│   │       │   ├── css/style.css                 # 样式文件
│   │       │   └── js/app.js                     # 前端逻辑
│   │       └── templates/                        # (预留)
│   └── test/
│       └── java/org/nox/NoxApplicationTests.java # 上下文加载测试
├── pom.xml                       # Maven 配置
├── mvnw / mvnw.cmd               # Maven Wrapper
└── README.md                     # 本文件
```

---

## ⚙️ 配置说明

编辑 `src/main/resources/application.properties`：

```properties
# 控制端服务端口
server.port=6789

# 应用名称
spring.application.name=Nox

# XOR 异或密钥 (需与 shell.jsp 保持一致)
xorKey=GRzGRYx7mRlIQgFd

# AES 加密密钥 (需与 shell.jsp 保持一致)
aesKey=VcApSBJ570GQhjrK

# AES-CBC 初始化向量 (需与 shell.jsp 保持一致)
aesIv=PKAXoyeRITP9JfbF
```

> **注意**：修改密钥后，必须同步更新 `server/shell.jsp` 和 `src/main/resources/JavaTemplates/` 下所有模板中的对应密钥值。

---

## 🖼️ 截图预览

> 待补充

---

## 📜 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

**再次强调**：本项目仅供安全研究与教育目的使用，严禁用于非法用途。任何因使用本项目而产生的法律责任，均由使用者自行承担。

---

## 🙏 致谢

感谢所有为网络安全研究做出贡献的开发者与研究者。

如有问题或建议，欢迎提交 Issue 或 Pull Request。
