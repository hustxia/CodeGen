package com.codegen.service.impl;

import com.codegen.common.exception.BusinessException;
import com.codegen.common.log.Ip2RegionSearcher;
import com.codegen.config.GenOutputProperties;
import com.codegen.config.I18nCatalog;
import com.codegen.config.LanguageConfig;
import com.codegen.dto.GenScaffoldDTO;
import com.codegen.service.GenScaffoldService;
import com.codegen.service.I18nMessageService;
import com.codegen.service.I18nTextRewriter;
import com.codegen.support.GenNaming;
import com.codegen.support.GenPathResolver;
import com.codegen.vo.GenScaffoldCheckVO;
import com.codegen.vo.GenScaffoldVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * V9.1 拆分自 {@link GenScaffoldServiceImpl}：脚手架共享工具：classpath 资源读取等（供各 Writer 静态引用）。
 * 纯函数集合，无实例状态；产物内容与拆分前逐字节一致。
 */
final class ScaffoldTextSupport {

    /**
     * 读脚手架资源（classpath 下的静态模板），缺失时抛业务异常而不是静默写空文件。
     */
    static String loadScaffoldResource(String path) {
        org.springframework.core.io.ClassPathResource resource =
                new org.springframework.core.io.ClassPathResource(path);
        try (java.io.InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("脚手架资源缺失：" + path, e);
        }
    }
}
