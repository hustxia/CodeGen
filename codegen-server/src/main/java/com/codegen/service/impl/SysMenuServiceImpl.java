package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codegen.entity.SysMenu;
import com.codegen.mapper.SysMenuMapper;
import com.codegen.service.SysMenuService;
import com.codegen.vo.SysMenuVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单 Service 实现
 */
@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    @Override
    public List<SysMenuVO> tree() {
        return tree(null, 1);
    }

    @Override
    public List<SysMenuVO> tree(Integer type, Integer status) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, SysMenu::getStatus, status);
        wrapper.eq(type != null, SysMenu::getType, type);
        wrapper.orderByAsc(SysMenu::getSort);
        List<SysMenu> list = list(wrapper);
        List<SysMenuVO> all = list.stream().map(e -> {
            SysMenuVO vo = new SysMenuVO();
            BeanUtils.copyProperties(e, vo);
            return vo;
        }).collect(Collectors.toList());
        Map<Long, List<SysMenuVO>> parentMap = all.stream()
                .collect(Collectors.groupingBy(SysMenuVO::getParentId));
        List<SysMenuVO> roots = parentMap.getOrDefault(0L, parentMap.getOrDefault(null, List.of()));
        roots.forEach(r -> buildChildren(r, parentMap));
        roots.sort(Comparator.comparingInt(SysMenuVO::getSort));
        return roots;
    }

    private void buildChildren(SysMenuVO node, Map<Long, List<SysMenuVO>> parentMap) {
        // 不能用 List.of() 当默认值：下面要原地 sort，不可变列表会直接 UnsupportedOperationException
        List<SysMenuVO> children = new ArrayList<>(parentMap.getOrDefault(node.getId(), List.of()));
        children.sort(Comparator.comparingInt(SysMenuVO::getSort));
        node.setChildren(children);
        children.forEach(c -> buildChildren(c, parentMap));
    }
}
