package cn.kuship.console.modules.misc.upload.controller;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.response.ApiResult;
import cn.kuship.console.common.response.GeneralMessage;
import cn.kuship.console.common.response.SkipResponseWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/** {@code POST /files/upload} 通用上传 + {@code GET /files/{id}} 下载。MVP 本地磁盘 5MB 上限。 */
@RestController
@RequestMapping("/console")
public class FileUploadController {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final Path uploadDir;

    public FileUploadController(@Value("${kuship.upload.dir:/tmp/kuship}") String dir) throws IOException {
        this.uploadDir = Paths.get(dir);
        Files.createDirectories(this.uploadDir);
    }

    @PostMapping(value = {"/files/upload", "/files/upload/"})
    public ApiResult upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ServiceHandleException(400, "empty file", "文件为空");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ServiceHandleException(400, "file too large", "文件超过 5MB");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null) {
            int dot = original.lastIndexOf('.');
            if (dot > 0) ext = original.substring(dot);
        }
        String id = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = uploadDir.resolve(id);
        try (var in = file.getInputStream()) {
            Files.copy(in, target);
        }
        return GeneralMessage.ok(Map.of(
                "file_url", "/console/files/" + id,
                "file_name", original != null ? original : id,
                "file_size", file.getSize()));
    }

    @GetMapping(value = "/files/{file_id}")
    @SkipResponseWrapper
    public ResponseEntity<byte[]> download(@PathVariable("file_id") String fileId) throws IOException {
        Path target = uploadDir.resolve(fileId).normalize();
        if (!target.startsWith(uploadDir) || !Files.exists(target)) {
            return ResponseEntity.notFound().build();
        }
        byte[] body = Files.readAllBytes(target);
        HttpHeaders headers = new HttpHeaders();
        String name = target.getFileName().toString();
        headers.setContentDispositionFormData("attachment", name);
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            headers.setContentType(MediaType.IMAGE_JPEG);
        } else {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }
        return new ResponseEntity<>(body, headers, 200);
    }
}
