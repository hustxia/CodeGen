package com.codegen.common.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * shop-common 自动装配入口
 * 通过 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 注册，
 * 使各业务服务能扫描到 com.codegen.common 包下的组件（全局异常、字段填充等）。
 */
@Configuration
@ComponentScan("com.codegen.common")
public class CommonAutoConfiguration {
}
