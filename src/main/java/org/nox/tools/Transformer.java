package org.nox.tools;

import org.nox.cipher.Cipher;

import javax.tools.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Payload 转换器。
 * 将 Java 源代码模板转换为最终的加密 Base64 字符串。
 * 处理流程：编译 → GZip 压缩 → XOR 异或 → AES-CBC 加密 → Base64 编码。
 */
public class Transformer {

    /**
     * 将 Java 源代码转换为加密后的 Base64 字符串。
     * @param code    Java 源代码字符串
     * @param xorKey  异或密钥
     * @param aesKey  AES 密钥
     * @param aesIv   AES 初始化向量
     * @return 加密后的 Base64 字符串
     * @throws Exception 编译或加密过程中的异常
     */
    public static String transform(String code, String xorKey, String aesKey, String aesIv) throws Exception {
        // 1. 编译 Java 源码为字节码
        byte[] byteCode = compile(code);
        // 2. GZip 压缩字节码，减少体积
        byte[] compressedCode = Cipher.compress(byteCode);
        // 3. 使用 XOR 对压缩后的数据进行异或混淆
        byte[] xoredCode = Cipher.xor(compressedCode, xorKey.getBytes(StandardCharsets.UTF_8));
        // 4. 使用 AES-CBC 对异或后的数据进行加密
        byte[] aesedCode = Cipher.aesCBC(xoredCode, aesKey.getBytes(StandardCharsets.UTF_8), aesIv.getBytes(StandardCharsets.UTF_8), 0);
        // 5. Base64 编码，便于在 HTTP 中传输
        return Base64.getEncoder().encodeToString(aesedCode);
    }

    /**
     * 编译 Java 源代码。
     * 将源码写入临时目录，调用系统 javac 编译，读取生成的 .class 文件后清理临时目录。
     * @param code Java 源代码
     * @return 编译后的字节码
     * @throws Exception 编译失败或 IO 异常
     */
    private static byte[] compile(String code) throws Exception {
        String className = extractClassName(code);
        Path tempDir = Files.createTempDirectory("noxdemo-compile-");
        try {
            Path sourceFile = writeSourceFile(code, className, tempDir);
            compileJavaSource(sourceFile, tempDir);

            Path classFile = resolveClassFile(code, className, tempDir);
            if (!Files.exists(classFile)) {
                throw new IOException("Compiled class not found: " + classFile);
            }
            return Files.readAllBytes(classFile);
        } finally {
            // 编译完成后清理临时目录
            deleteDirectory(tempDir);
        }
    }

    /**
     * 将源代码写入临时目录的 .java 文件中。
     * 如果代码中包含 package 声明，会自动创建对应的包目录结构。
     */
    private static Path writeSourceFile(String code, String className, Path tempDir) throws IOException {
        String packageName = extractPackageName(code);
        Path pkgDir = tempDir;
        if (!packageName.isEmpty()) {
            pkgDir = tempDir.resolve(packageName.replace('.', '/'));
            Files.createDirectories(pkgDir);
        }
        Path sourceFile = pkgDir.resolve(className + ".java");
        Files.write(sourceFile, code.getBytes(StandardCharsets.UTF_8));
        return sourceFile;
    }

    /**
     * 调用系统 Java 编译器编译源码文件。
     * @param sourcePath 源码文件路径
     * @param outputDir  编译输出目录
     */
    private static void compileJavaSource(Path sourcePath, Path outputDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler available. Please run on a JDK, not a JRE.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                    Collections.singletonList(sourcePath.toFile())
            );

            String classpath = System.getProperty("java.class.path");
            List<String> options = Arrays.asList(
                    "-d", outputDir.toString(),
                    "-classpath", classpath
            );

            Boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits).call();
            if (success == null || !success) {
                StringBuilder error = new StringBuilder("Compilation failed:\n");
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    error.append(diagnostic.getKind())
                            .append(" ")
                            .append(diagnostic.getMessage(null))
                            .append("\n");
                }
                throw new IllegalStateException(error.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException("Compile error", e);
        }
    }

    /**
     * 根据包名和类名定位编译后的 .class 文件路径。
     */
    private static Path resolveClassFile(String code, String className, Path tempDir) {
        String packageName = extractPackageName(code);
        if (packageName.isEmpty()) {
            return tempDir.resolve(className + ".class");
        }
        return tempDir.resolve(packageName.replace('.', '/')).resolve(className + ".class");
    }

    /**
     * 从源码中提取 package 声明。
     * @return 包名，如果不存在则返回空字符串
     */
    private static String extractPackageName(String code) {
        Matcher matcher = Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z_][\\w.]*)\\s*;").matcher(code);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * 从源码中提取类名。
     * 优先匹配 public class，其次匹配普通 class。
     * @return 类名
     */
    private static String extractClassName(String code) {
        Matcher matcher = Pattern.compile("(?m)\\bpublic\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)\\b").matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = Pattern.compile("(?m)\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)\\b").matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("Cannot determine class name from code");
    }

    /**
     * 递归删除目录及其内容。
     * 用于清理编译产生的临时文件。
     */
    private static void deleteDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try {
            java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(dir);
            List<Path> entries = new ArrayList<>();
            for (Path entry : stream) {
                entries.add(entry);
            }
            stream.close();
            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    deleteDirectory(entry);
                } else {
                    try {
                        Files.deleteIfExists(entry);
                    } catch (IOException ignored) {
                    }
                }
            }
            try {
                Files.deleteIfExists(dir);
            } catch (IOException ignored) {
            }
        } catch (IOException ignored) {
        }
    }
}
