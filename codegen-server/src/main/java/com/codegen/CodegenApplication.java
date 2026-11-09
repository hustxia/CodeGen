package com.codegen;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 代码生成器平台启动类。
 *
 * <p>独立单体：不依赖 Nacos / 网关 / Redis，连上 MySQL 即可运行。
 */
@SpringBootApplication
@MapperScan("com.codegen.mapper")
public class CodegenApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodegenApplication.class, args);
    }
}
