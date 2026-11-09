package __PKG__.common.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 文件能力包配置：上传目录 / 大小上限 / 扩展名白名单。
 * yml 未配置时全部有内置默认，产物零配置开箱即跑。
 *
 * <p>白名单不用 SpEL List 注入（#{'${...}'.split(',')} 与占位符默认值含逗号
 * 组合的解析顺序在部分 Spring 版本下拿到的不是预期列表——实测 .png 被拒），
 * 改 String 注入后手动 split，行为直观可测。
 */
@Component
public class FileProps {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${file.max-size-mb:10}")
    private long maxSizeMb;

    /**
     * 上传扩展名白名单：图片 + 常见文档，逗号分隔（不带点）。
     * 需要放开更多类型在 yml 覆盖 file.allowed-exts。
     *
     * <p><b>刻意不含 svg</b>：/uploads/** 是免鉴权静态直链（见 FileWebConfig），
     * 而 SVG 可内嵌 &lt;script&gt;，同源直开等于存储型 XSS（能读 localStorage 里的 token）。
     * 确需上传 SVG 时，除了把 svg 加进白名单，还必须自行给 /uploads 配独立域名或加沙箱响应头。
     */
    @Value("${file.allowed-exts:jpg,jpeg,png,gif,webp,bmp,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,csv,zip,rar,7z,mp3,mp4}")
    private String allowedExtsRaw;

    public String getUploadDir() {
        return uploadDir;
    }

    public long getMaxSizeMb() {
        return maxSizeMb;
    }

    /** 上限字节数 */
    public long maxBytes() {
        return maxSizeMb * 1024L * 1024L;
    }

    public List<String> getAllowedExts() {
        return Arrays.stream(allowedExtsRaw.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
