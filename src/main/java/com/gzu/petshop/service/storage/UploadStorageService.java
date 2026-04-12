package com.gzu.petshop.service.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

/**
 * 商品图片等文件落盘，返回可供前端直接引用的相对路径 {@code /uploads/...}。
 */
@Service
public class UploadStorageService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * 确保上传根目录存在。
     */
    @PostConstruct
    public void init() throws IOException {
        Path p = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(p);
    }

    /**
     * 保存图片文件，仅允许常见图片类型。
     *
     * @param file 表单文件
     * @return 形如 {@code /uploads/uuid.jpg} 的 Web 路径
     * @throws IOException              写入失败
     * @throws IllegalArgumentException 类型不合法
     */
    public String storeProductImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择图片文件");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("仅支持图片文件");
        }
        String ext = resolveExtension(file.getOriginalFilename(), ct);
        String name = UUID.randomUUID() + ext;
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path dest = root.resolve(name);
        file.transferTo(dest.toFile());
        return "/uploads/" + name;
    }

    private static String resolveExtension(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (ext.matches("\\.(jpg|jpeg|png|gif|webp)")) {
                return ext;
            }
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        if (ct.contains("png")) {
            return ".png";
        }
        if (ct.contains("jpeg") || ct.contains("jpg")) {
            return ".jpg";
        }
        if (ct.contains("gif")) {
            return ".gif";
        }
        if (ct.contains("webp")) {
            return ".webp";
        }
        return ".jpg";
    }
}
