package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codegen.dto.GenOutputPageDTO;
import com.codegen.entity.GenOutput;
import com.codegen.mapper.GenOutputMapper;
import com.codegen.service.GenOutputService;
import com.codegen.vo.GenOutputBatchVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 写盘台账实现：分页走 MyBatis-Plus，批次摘要走 SQL 聚合（表数量有限，聚合代价可接受）
 */
@Service
public class GenOutputServiceImpl implements GenOutputService {

    @Autowired
    private GenOutputMapper genOutputMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public IPage<GenOutput> pageList(GenOutputPageDTO dto) {
        LambdaQueryWrapper<GenOutput> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(dto.getTableName()), GenOutput::getTableName, dto.getTableName());
        wrapper.eq(StringUtils.hasText(dto.getBatchNo()), GenOutput::getBatchNo, dto.getBatchNo());
        wrapper.eq(StringUtils.hasText(dto.getAction()), GenOutput::getAction, dto.getAction());
        wrapper.orderByDesc(GenOutput::getId);
        int pageNum = dto.getPageNum() == null || dto.getPageNum() < 1 ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() < 1 ? 10 : dto.getPageSize();
        return genOutputMapper.selectPage(Page.of(pageNum, pageSize), wrapper);
    }

    @Override
    public List<GenOutputBatchVO> batchSummary(int limit) {
        int size = limit <= 0 ? 20 : Math.min(limit, 100);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT batch_no, COUNT(DISTINCT table_name) AS table_cnt, COUNT(*) AS files, "
                        + "SUM(action = 'WRITTEN') AS written, SUM(action = 'OVERWRITTEN') AS overwritten, "
                        + "SUM(action = 'MERGED') AS merged, SUM(action = 'SKIPPED') AS skipped, "
                        + "SUM(action = 'ERROR') AS errors, SUM(file_size) AS total_bytes, "
                        + "MAX(operator) AS operator, MAX(create_time) AS last_time "
                        + "FROM gen_output WHERE deleted = 0 GROUP BY batch_no ORDER BY MAX(create_time) DESC LIMIT " + size);
        return rows.stream().map(this::toVO).toList();
    }

    private GenOutputBatchVO toVO(Map<String, Object> row) {
        GenOutputBatchVO vo = new GenOutputBatchVO();
        vo.setBatchNo((String) row.get("batch_no"));
        vo.setTables(intOf(row.get("table_cnt")));
        vo.setFiles(intOf(row.get("files")));
        vo.setWritten(intOf(row.get("written")));
        vo.setOverwritten(intOf(row.get("overwritten")));
        vo.setMerged(intOf(row.get("merged")));
        vo.setSkipped(intOf(row.get("skipped")));
        vo.setErrors(intOf(row.get("errors")));
        Object bytes = row.get("total_bytes");
        vo.setTotalBytes(bytes == null ? 0L : ((Number) bytes).longValue());
        vo.setOperator((String) row.get("operator"));
        Object last = row.get("last_time");
        vo.setLastTime(last == null ? null : (LocalDateTime) last);
        return vo;
    }

    private Integer intOf(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
