package __PKG__.common.file;

import __PKG__.common.context.UserContext;
import __PKG__.common.exception.BusinessException;
import __PKG__.common.result.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传下载基座：
 * <ul>
 *   <li>POST /file/upload —— 校验白名单+大小 → yyyyMMdd/uuid.ext 落盘 → 插 sys_file</li>
 *   <li>GET /file/download/{id} —— 按 id 查库取相对路径流式下载（双重防穿越）</li>
 *   <li>POST /file/page、DELETE /file/{id} —— 文件管理（P1 页面接入）</li>
 *   <li>GET /uploads/** —— 图片静态直链（FileWebConfig 映射，不走鉴权）</li>
 * </ul>
 */
@RestController
@RequestMapping("/file")
public class FileController {

    /** 分页上限：文件管理页不需要更大，防止 pageSize 传超大值打成全表扫描 */
    private static final long MAX_PAGE_SIZE = 500;

    @Autowired
    private FileMapper fileMapper;

    @Autowired
    private FileProps props;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的文件");
        }
        if (file.getSize() > props.maxBytes()) {
            throw new BusinessException(400, "文件超过大小限制（" + props.getMaxSizeMb() + "MB）");
        }
        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename());
        String ext = extOf(original);
        if (ext.isEmpty() || !props.getAllowedExts().contains(ext)) {
            throw new BusinessException(400, "不支持的文件类型：." + (ext.isEmpty() ? "无扩展名" : ext));
        }
        String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String storageName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Path dir = Paths.get(props.getUploadDir(), dateDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(storageName));
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败，请检查 upload-dir 配置与磁盘权限");
        }
        FileRecord record = new FileRecord();
        record.setOriginalName(original);
        record.setStorageName(storageName);
        record.setRelativePath(dateDir + "/" + storageName);
        record.setUrl("/uploads/" + dateDir + "/" + storageName);
        record.setContentType(file.getContentType());
        record.setFileSize(file.getSize());
        record.setCreateBy(UserContext.getUsername());
        fileMapper.insert(record);
        Map<String, Object> data = new HashMap<>();
        // id 必须显式转字符串：LongIdJacksonConfig 的 Long→String 只作用于 bean 属性，
        // Map 值会原样输出 number，19 位雪花号在 JS 里静默截断（S8 实测：回传 id 查不到记录）
        data.put("id", String.valueOf(record.getId()));
        data.put("url", record.getUrl());
        data.put("name", original);
        data.put("size", file.getSize());
        return Result.ok(data);
    }

    @GetMapping("/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        FileRecord record = fileMapper.selectById(id);
        if (record == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
            return;
        }
        // 双保险防穿越：normalize 后必须仍在上传目录内（relative_path 被篡改含 ../ 时拒绝）
        Path base = Paths.get(props.getUploadDir()).toAbsolutePath().normalize();
        Path target = base.resolve(record.getRelativePath()).normalize();
        if (!target.startsWith(base) || !Files.exists(target)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在或已被清理");
            return;
        }
        response.setContentType(record.getContentType() == null
                ? "application/octet-stream" : record.getContentType());
        response.setContentLengthLong(Files.size(target));
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(record.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20"));
        Files.copy(target, response.getOutputStream());
        response.getOutputStream().flush();
    }

    @PostMapping("/page")
    public Result<Page<FileRecord>> page(@RequestBody Map<String, Object> req) {
        // 分页参数容错 + 钳位：直接 Long.parseLong 遇到非法值抛 NumberFormatException（变成 500）；
        // pageSize 不设上限则可能被一次刷成全表扫描
        long pageNum = Math.max(1L, parseLong(req.get("pageNum"), 1L));
        long pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1L, parseLong(req.get("pageSize"), 10L)));
        String name = req.get("originalName") == null ? "" : String.valueOf(req.get("originalName")).trim();
        LambdaQueryWrapper<FileRecord> qw = new LambdaQueryWrapper<>();
        if (!name.isEmpty()) {
            qw.like(FileRecord::getOriginalName, name);
        }
        qw.orderByDesc(FileRecord::getId);
        return Result.ok(fileMapper.selectPage(new Page<>(pageNum, pageSize), qw));
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        // 逻辑删：物理文件保留（防误删不可恢复；清理策略留给运维脚本）
        fileMapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 宽松解析分页参数：null / 空串 / 非数字一律回落默认值，不抛异常。
     */
    private long parseLong(Object value, long defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 小写扩展名（**不带点**，与 file.allowed-exts 白名单同口径）；无扩展名返回空串。
     * 带点/不带点混用是 S8 实测翻车点：extOf 返回 ".png" 而白名单是 "png"，
     * contains 恒 false，所有上传都被拒。
     */
    private String extOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        // Locale.ROOT：土耳其语环境下 "GIF".toLowerCase() 会得到 "gıf"，与白名单恒不匹配
        return name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
