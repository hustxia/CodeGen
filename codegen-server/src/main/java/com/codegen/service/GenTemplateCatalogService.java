package com.codegen.service;

import com.codegen.vo.GenTemplateListItemVO;

import java.util.List;

/**
 * 模板文件清单服务（只读）：扫描 classpath 下的 *.ftl 模板并给出粗略引用次数。
 */
public interface GenTemplateCatalogService {

    /**
     * 列出所有模板：模板相对路径、所属目录、引用次数。
     */
    List<GenTemplateListItemVO> listTemplates();
}
