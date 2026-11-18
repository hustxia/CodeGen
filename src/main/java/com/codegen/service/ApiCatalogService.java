package com.codegen.service;

import com.codegen.vo.ApiCatalogItemVO;

import java.util.List;

/**
 * P2-5 接口文档聚合。
 *
 * <p>开发过程中最常见的沟通成本是「这个功能调哪个接口、要什么权限」。
 * 这里直接从生成配置推导出接口清单，和真正生成出来的 Controller 一一对应，
 * 不需要额外维护文档，也不会因为忘记更新文档而失真。
 */
public interface ApiCatalogService {

    /**
     * 全部表的接口目录；tableId 非空时只看该表。
     */
    List<ApiCatalogItemVO> catalog(Long tableId, String keyword);
}
