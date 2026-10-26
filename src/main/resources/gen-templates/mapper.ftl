package ${pkg}.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ${pkg}.entity.${className};
import org.apache.ibatis.annotations.Mapper;

/**
 * ${comment} Mapper
<#if hasDataScope> *
 * <p>数据权限：本表按 {@code ${scopeColumn}} 做 ${scopeType} 级隔离，
 * 登记在启动类 {@code common.datascope.DataScopeRegistry} 里，查询与更新都会自动追加条件。
</#if>
 */
@Mapper
public interface ${className}Mapper extends BaseMapper<${className}> {

    // <gen:begin key="mapper-body">
    // <gen:end key="mapper-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====
}
