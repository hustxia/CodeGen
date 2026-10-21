package com.codegen.service;

import com.codegen.dto.GenApplyDTO;
import com.codegen.vo.GenApplyVO;

import java.util.List;
import java.util.Map;

/**
 * 一键生成：把 V1.1 里靠人记顺序的四个动作串成一条流水线。
 *
 * <p>顺序不是随便定的：路由文件指向的 {@code views/<module>/index.vue} 必须先落盘，
 * 否则菜单点进去 404。所以固定为 自检 → 备份 → 写盘 →（编译）→ 注册菜单 → 重建路由。
 */
public interface GenApplyService {

    /**
     * 执行一键生成
     */
    GenApplyVO apply(GenApplyDTO dto);

    /**
     * 回滚某个批次：有备份的还原，首次生成的文件删除
     *
     * @return 还原/删除的文件数
     */
    Map<String, Object> rollback(String batchNo);

    /**
     * 最近的写盘批次
     */
    List<Map<String, Object>> recentBatches(int limit);
}
