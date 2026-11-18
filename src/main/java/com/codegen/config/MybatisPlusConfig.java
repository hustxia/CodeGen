package com.codegen.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.codegen.common.datascope.DataScopeInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>两个拦截器都必须注册：
 * <ul>
 *   <li>数据权限拦截器——按登录用户的范围改写 SQL</li>
 *   <li>分页拦截器——缺了它 Page 查询不执行 count，total 恒为 0（静默故障，很难发现）</li>
 * </ul>
 * 顺序上数据权限在前：先缩小可见数据再分页，否则不该看的行也会被算进总数。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new DataScopeInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
