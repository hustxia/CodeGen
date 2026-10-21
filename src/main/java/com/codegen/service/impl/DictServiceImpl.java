package com.codegen.service.impl;

import com.codegen.dto.DictDataSaveDTO;
import com.codegen.dto.DictTypeSaveDTO;
import com.codegen.service.DictService;
import com.codegen.vo.DictDataVO;
import com.codegen.vo.DictTypeVO;
import com.codegen.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 字典维护实现。
 *
 * <p>约束：字典编码是生成页 {@code dict_type} 的引用目标，一旦被引用就不能随意改名，
 * 所以这里只允许新增/删除，改名需前端二次确认（调用方先删后建）。
 */
@Service
public class DictServiceImpl implements DictService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,64}");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public List<DictTypeVO> listTypes(String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT t.id, t.dict_code, t.dict_name, t.status, t.created_at, t.updated_at, "
                        + "(SELECT COUNT(*) FROM sys_dict_data d WHERE d.dict_code = t.dict_code) AS data_count "
                        + "FROM sys_dict_type t WHERE t.deleted = 0");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            sql.append(" AND (t.dict_code LIKE ? OR t.dict_name LIKE ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        sql.append(" ORDER BY t.dict_code");
        return jdbcTemplate.query(sql.toString(), (rs, i) -> {
            DictTypeVO vo = new DictTypeVO();
            vo.setId(rs.getLong("id"));
            vo.setDictCode(rs.getString("dict_code"));
            vo.setDictName(rs.getString("dict_name"));
            vo.setStatus(rs.getInt("status"));
            vo.setDataCount(rs.getInt("data_count"));
            if (rs.getTimestamp("created_at") != null) {
                vo.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
            if (rs.getTimestamp("updated_at") != null) {
                vo.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            }
            return vo;
        }, args.toArray());
    }

    @Override
    public Long saveType(DictTypeSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getDictCode())) {
            throw new BusinessException(400, "字典编码不能为空");
        }
        String code = dto.getDictCode().trim();
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException(400, "字典编码只允许字母、数字、下划线，且不超过 64 位");
        }
        String name = StringUtils.hasText(dto.getDictName()) ? dto.getDictName().trim() : code;
        Integer status = dto.getStatus() == null ? 1 : dto.getStatus();

        if (dto.getId() != null) {
            jdbcTemplate.update("UPDATE sys_dict_type SET dict_name = ?, status = ? WHERE id = ? AND deleted = 0",
                    name, status, dto.getId());
            return dto.getId();
        }
        // 同 code 已被逻辑删除的行会占用唯一键，先复活它
        List<Long> existing = jdbcTemplate.queryForList(
                "SELECT id FROM sys_dict_type WHERE dict_code = ?", Long.class, code);
        if (!existing.isEmpty()) {
            Long id = existing.get(0);
            jdbcTemplate.update("UPDATE sys_dict_type SET dict_name = ?, status = ?, deleted = 0 WHERE id = ?",
                    name, status, id);
            return id;
        }
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO sys_dict_type(dict_code, dict_name, status, deleted) VALUES(?, ?, ?, 0)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setInt(3, status);
            return ps;
        }, holder);
        Number key = holder.getKey();
        return key == null ? null : key.longValue();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteType(Long id) {
        if (id == null) {
            throw new BusinessException(400, "字典类型主键不能为空");
        }
        List<String> codes = jdbcTemplate.queryForList(
                "SELECT dict_code FROM sys_dict_type WHERE id = ? AND deleted = 0", String.class, id);
        if (codes.isEmpty()) {
            return 0;
        }
        String code = codes.get(0);
        int removed = jdbcTemplate.update("DELETE FROM sys_dict_data WHERE dict_code = ?", code);
        jdbcTemplate.update("DELETE FROM sys_dict_type WHERE id = ?", id);
        return removed;
    }

    @Override
    public List<DictDataVO> listData(String dictCode) {
        if (!StringUtils.hasText(dictCode)) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT id, dict_code, dict_label, dict_value, sort_no, status, remark "
                        + "FROM sys_dict_data WHERE dict_code = ? ORDER BY sort_no, id",
                (rs, i) -> {
                    DictDataVO vo = new DictDataVO();
                    vo.setId(rs.getLong("id"));
                    vo.setDictCode(rs.getString("dict_code"));
                    vo.setDictLabel(rs.getString("dict_label"));
                    vo.setDictValue(rs.getString("dict_value"));
                    vo.setSortNo(rs.getInt("sort_no"));
                    vo.setStatus(rs.getInt("status"));
                    vo.setRemark(rs.getString("remark"));
                    return vo;
                }, dictCode);
    }

    @Override
    public Long saveData(DictDataSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getDictCode())) {
            throw new BusinessException(400, "字典编码不能为空");
        }
        if (!StringUtils.hasText(dto.getDictLabel()) || !StringUtils.hasText(dto.getDictValue())) {
            throw new BusinessException(400, "字典标签与字典值都不能为空");
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_type WHERE dict_code = ? AND deleted = 0",
                Integer.class, dto.getDictCode());
        if (count == null || count == 0) {
            throw new BusinessException(400, "字典类型不存在：" + dto.getDictCode() + "，请先创建类型");
        }
        Integer sortNo = dto.getSortNo() == null ? nextSortNo(dto.getDictCode()) : dto.getSortNo();
        Integer status = dto.getStatus() == null ? 1 : dto.getStatus();
        if (dto.getId() != null) {
            jdbcTemplate.update("UPDATE sys_dict_data SET dict_label = ?, dict_value = ?, sort_no = ?, "
                            + "status = ?, remark = ? WHERE id = ?",
                    dto.getDictLabel().trim(), dto.getDictValue().trim(), sortNo, status,
                    dto.getRemark(), dto.getId());
            return dto.getId();
        }
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO sys_dict_data(dict_code, dict_label, dict_value, sort_no, status, remark) "
                            + "VALUES(?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, dto.getDictCode());
            ps.setString(2, dto.getDictLabel().trim());
            ps.setString(3, dto.getDictValue().trim());
            ps.setInt(4, sortNo);
            ps.setInt(5, status);
            ps.setString(6, dto.getRemark());
            return ps;
        }, holder);
        Number key = holder.getKey();
        return key == null ? null : key.longValue();
    }

    @Override
    public void deleteData(Long id) {
        if (id != null) {
            jdbcTemplate.update("DELETE FROM sys_dict_data WHERE id = ?", id);
        }
    }

    @Override
    public List<String> listOrphans() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT d.dict_code FROM sys_dict_data d "
                        + "WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type t "
                        + "WHERE t.dict_code = d.dict_code AND t.deleted = 0) ORDER BY d.dict_code",
                String.class);
    }

    @Override
    public int repairOrphans() {
        List<String> orphans = listOrphans();
        for (String code : orphans) {
            jdbcTemplate.update("INSERT INTO sys_dict_type(dict_code, dict_name, status, deleted) "
                    + "VALUES(?, ?, 1, 0)", code, code);
        }
        return orphans.size();
    }

    private int nextSortNo(String dictCode) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT IFNULL(MAX(sort_no), 0) + 1 FROM sys_dict_data WHERE dict_code = ?",
                Integer.class, dictCode);
        return max == null ? 1 : max;
    }
}
