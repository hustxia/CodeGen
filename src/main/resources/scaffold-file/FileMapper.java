package __PKG__.common.file;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 文件元数据 Mapper。启动类 @MapperScan 带 markerInterface=BaseMapper 过滤，
 * common/file 包里的非 Mapper 类（FileController/FileProps）不会被误注册。
 */
public interface FileMapper extends BaseMapper<FileRecord> {
}
