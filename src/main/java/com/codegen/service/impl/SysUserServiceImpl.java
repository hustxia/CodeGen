package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codegen.dto.LoginDTO;
import com.codegen.dto.SysUserSaveDTO;
import com.codegen.entity.SysUser;
import com.codegen.entity.SysUserRole;
import com.codegen.mapper.SysUserMapper;
import com.codegen.service.PermissionService;
import com.codegen.service.SysUserRoleService;
import com.codegen.service.SysUserService;
import com.codegen.util.JwtUtil;
import com.codegen.vo.LoginVO;
import com.codegen.vo.SysUserVO;
import com.codegen.common.exception.BusinessException;
import com.codegen.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 平台用户 Service 实现
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private static final Logger log = LoggerFactory.getLogger(SysUserServiceImpl.class);

    /**
     * 主题 ID 格式：三段小写字母，用连字符连接，如 blue-light-rounded。
     * 只校验形状不校验取值，理由见 {@link #saveTheme(Long, String)}。
     */
    private static final Pattern THEME_ID_PATTERN =
            Pattern.compile("^[a-z]{2,20}(-[a-z]{2,20}){2}$");

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SysUserRoleService userRoleService;

    @Autowired
    private PermissionService permissionService;

    @Override
    public LoginVO login(LoginDTO dto) {
        // 先判空：BCryptPasswordEncoder.matches(null, hash) 会抛 IllegalArgumentException，
        // 落到全局兜底就变成 500「服务异常」，前端拿不到「用户名或密码错误」这句可读提示
        if (dto == null || !StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, dto.getUsername());
        SysUser user = getOne(wrapper);
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "账号已禁用");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        // 主题随登录一起下发：前端收到后立刻套用并写入 localStorage，
        // 下次刷新首屏就能直接渲染正确皮肤
        vo.setThemeId(user.getThemeId());
        return vo;
    }

    @Override
    public SysUserVO getByToken(String token) {
        try {
            Long userId = jwtUtil.userIdOf(token);
            if (userId == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "token 缺少用户标识");
            }
            SysUser user = getById(userId);
            if (user == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "用户不存在");
            }
            SysUserVO vo = new SysUserVO();
            BeanUtils.copyProperties(user, vo);
            // 权限码随用户信息一起下发：前端按钮级控制不再依赖硬编码角色。
            // 权限查询失败不能连累登录态——降级为空集合 + 告警，用户信息照常返回。
            try {
                vo.setPermissions(new ArrayList<>(permissionService.permissionsOf(user.getId())));
            } catch (Exception e) {
                log.warn("查询权限码失败（userId={}）：{} {}", user.getId(), e.getClass().getSimpleName(), e.getMessage());
                vo.setPermissions(new ArrayList<>());
            }
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("解析 token 失败：{} {}", e.getClass().getSimpleName(), e.getMessage());
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "token无效或已过期");
        }
    }

    @Override
    public void saveUser(SysUserSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getUsername())) {
            throw new BusinessException(400, "用户名不能为空");
        }
        // 这里必须拦住：密码留空会写入 NULL，该账号能建出来但永远登录不上，
        // 而且没有任何提示（bcrypt 对空串/空值一律判不匹配）
        if (!StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException(400, "新增用户必须设置初始密码");
        }
        SysUser entity = new SysUser();
        BeanUtils.copyProperties(dto, entity);
        if (StringUtils.hasText(dto.getPassword())) {
            entity.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        save(entity);
    }

    @Override
    public void updateUser(SysUserSaveDTO dto) {
        SysUser entity = new SysUser();
        BeanUtils.copyProperties(dto, entity);
        if (StringUtils.hasText(dto.getPassword())) {
            entity.setPassword(passwordEncoder.encode(dto.getPassword()));
        } else {
            entity.setPassword(null);
        }
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserRole::getUserId, userId);
        userRoleService.remove(wrapper);
        if (roleIds != null && !roleIds.isEmpty()) {
            List<SysUserRole> list = roleIds.stream().map(roleId -> {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                return ur;
            }).collect(Collectors.toList());
            userRoleService.saveBatch(list);
        }
    }

    /**
     * 保存界面主题偏好。
     *
     * <p>只做格式校验（色相-明暗-形态三段小写字母），不在后端维护色相白名单：
     * 主题体系属于前端表现层，后端硬编码枚举会导致「前端加主题、后端要跟着发版」。
     * 格式对但色相不存在时，前端 normalizeThemeId 会回落到默认主题，不会白屏。
     */
    @Override
    public String saveTheme(Long userId, String themeId) {
        if (!StringUtils.hasText(themeId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "主题 ID 不能为空");
        }
        String normalized = themeId.trim().toLowerCase();
        if (!THEME_ID_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "主题 ID 格式不合法，应形如 blue-light-rounded");
        }
        SysUser entity = new SysUser();
        entity.setId(userId);
        entity.setThemeId(normalized);
        updateById(entity);
        log.info("用户 {} 更新界面主题为 {}", userId, normalized);
        return normalized;
    }
}
