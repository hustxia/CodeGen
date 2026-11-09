package com.codegen.service;

import com.codegen.dto.GenScaffoldDTO;
import com.codegen.vo.GenScaffoldCheckVO;
import com.codegen.vo.GenScaffoldVO;

/**
 * 一键建工程：产出「从零能跑」的空工程，含已验证的基座类。
 */
public interface GenScaffoldService {

    /**
     * 生成空工程（幂等：已存在文件默认跳过）
     */
    GenScaffoldVO scaffold(GenScaffoldDTO dto);

    /**
     * 体检目标工程：缺什么、怎么补
     *
     * @param backendRoot  后端工程根（绝对路径，可为空则用配置）
     * @param frontendRoot 前端工程根（绝对路径，可为空则用配置）
     * @param rootPackage  基座包名（可为空则用配置）
     */
    GenScaffoldCheckVO check(String backendRoot, String frontendRoot, String rootPackage);
}
