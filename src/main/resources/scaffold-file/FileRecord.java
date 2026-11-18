package __PKG__.common.file;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件元数据：落盘文件 + 库里一条记录，url 即前端可直接引用的相对路径。
 * 刻意不继承 BaseEntity（基座资源文件自包含，避免与实体模板结构耦合）。
 */
@Data
@TableName("sys_file")
public class FileRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 原始文件名 */
    private String originalName;

    /** 落盘文件名（uuid.ext） */
    private String storageName;

    /** 相对 upload-dir 的路径（yyyyMMdd/uuid.ext） */
    private String relativePath;

    /** 访问URL（/uploads/...） */
    private String url;

    /** MIME类型 */
    private String contentType;

    /** 字节数 */
    private Long fileSize;

    /** MD5（预留去重） */
    private String md5;

    /** 业务归类（预留） */
    private String bizType;

    @TableLogic
    private Integer deleted;

    /** 创建人（上传时手动填 UserContext.username；基座审计填充器是 Long 语义，不通用） */
    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private String remark;
}
