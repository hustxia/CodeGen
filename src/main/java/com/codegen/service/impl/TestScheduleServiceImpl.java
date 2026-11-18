package com.codegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codegen.common.exception.BusinessException;
import com.codegen.dto.TestCenterDTO;
import com.codegen.entity.TestRun;
import com.codegen.entity.TestSchedule;
import com.codegen.mapper.TestRunMapper;
import com.codegen.mapper.TestScheduleMapper;
import com.codegen.service.TestRunService;
import com.codegen.service.TestScheduleService;
import com.codegen.vo.TestVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * V9.2 定时回归实现。
 *
 * <p>触发模型：{@code @Scheduled} 每 30s 调一次 {@link #scanAndTrigger()}，
 * 对每条启用任务做「到期判定 → 行锁认领 → 触发执行」。关键约束：
 *
 * <ul>
 *   <li><b>行锁防重复</b>：认领在独立事务内用 {@code SELECT ... FOR UPDATE} 加锁，
 *       把 {@code lastRunId} 置为哨兵 -1 后立即提交释放锁；另一实例读到 -1 会跳过，
 *       因此同一任务同一时刻只会在一个实例上触发一次。</li>
 *   <li><b>长任务不持锁</b>：实际执行（可能数分钟）在认领事务提交之后进行，
 *       不占用数据库行锁，也不阻塞下一次扫描。</li>
 *   <li><b>到期判定</b>：从最近批次的 started_at 起算 cron 的下一个触发点，
 *       到点即触发；进程崩溃残留的 -1 由 {@link #resetStuckClaims()} 在启动时复位。</li>
 * </ul>
 */
@Service
public class TestScheduleServiceImpl extends ServiceImpl<TestScheduleMapper, TestSchedule>
        implements TestScheduleService {

    private static final Logger log = LoggerFactory.getLogger(TestScheduleServiceImpl.class);
    /** 认领哨兵：表示本任务正在被某实例执行 */
    private static final long CLAIMED = -1L;

    @Autowired
    private TestRunService runService;

    @Autowired
    private TestRunMapper runMapper;

    @Override
    public Page<TestSchedule> page(TestCenterDTO.SchedulePage dto) {
        Page<TestSchedule> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<TestSchedule> w = new LambdaQueryWrapper<>();
        w.eq(dto.getProjectId() != null, TestSchedule::getProjectId, dto.getProjectId());
        w.like(dto.getName() != null, TestSchedule::getName, dto.getName());
        w.eq(TestSchedule::getDeleted, 0);
        w.orderByDesc(TestSchedule::getCreateTime);
        return baseMapper.selectPage(page, w);
    }

    @Override
    public TestSchedule getById(Long id) {
        TestSchedule s = baseMapper.selectById(id);
        if (s == null || Integer.valueOf(1).equals(s.getDeleted())) {
            throw new BusinessException(400, "任务不存在");
        }
        return s;
    }

    @Override
    public Long save(TestCenterDTO.ScheduleSave dto) {
        if (dto == null || !StringUtils.hasText(dto.getName()) || dto.getEnvId() == null) {
            throw new BusinessException(400, "任务名与执行环境必填");
        }
        if (!StringUtils.hasText(dto.getCron()) || !StringUtils.hasText(dto.getSuiteIds())) {
            throw new BusinessException(400, "cron 与套件列表必填");
        }
        // 提前校验 cron 合法性，避免存进一个永远触发的脏表达式
        try {
            CronExpression.parse(normalizeCron(dto.getCron()));
        } catch (Exception e) {
            throw new BusinessException(400, "cron 表达式非法: " + dto.getCron());
        }
        TestSchedule entity = dto.getId() == null ? new TestSchedule() : getById(dto.getId());
        entity.setProjectId(dto.getProjectId());
        entity.setName(dto.getName());
        entity.setEnvId(dto.getEnvId());
        entity.setSuiteIds(dto.getSuiteIds());
        entity.setCron(dto.getCron());
        entity.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        entity.setNotifyType(dto.getNotifyType());
        entity.setNotifyTarget(dto.getNotifyTarget());
        if (entity.getId() == null) {
            baseMapper.insert(entity);
        } else {
            baseMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    public void remove(Long id) {
        // 注意：TestSchedule 继承的 deleted 字段带 @TableLogic。
        // 普通 updateById 会忽略逻辑删除字段（SET 子句不生成 deleted=1），
        // 导致行无法被「删除」。必须用 deleteById，由 MyBatis-Plus 的
        // @TableLogic 机制将其转换为 UPDATE ... SET deleted=1 WHERE id=? AND deleted=0。
        baseMapper.deleteById(id);
    }

    @Override
    public void setEnabled(Long id, int enabled) {
        TestSchedule s = new TestSchedule();
        s.setId(id);
        s.setEnabled(enabled);
        baseMapper.updateById(s);
    }

    @Override
    public void scanAndTrigger() {
        List<TestSchedule> due = baseMapper.selectList(new LambdaQueryWrapper<TestSchedule>()
                .eq(TestSchedule::getEnabled, 1)
                .eq(TestSchedule::getDeleted, 0));
        for (TestSchedule s : due) {
            List<Long> suiteIds = parseSuiteIds(s.getSuiteIds());
            if (suiteIds.isEmpty()) {
                continue;
            }
            if (!claim(s.getId())) {
                continue;
            }
            TestCenterDTO.RunRequest req = new TestCenterDTO.RunRequest();
            req.setEnvId(s.getEnvId());
            req.setSuiteIds(suiteIds);
            req.setTriggerType("schedule");
            try {
                TestVO.RunDetail detail = runService.start(req);
                markRun(s.getId(), detail.run().getId());
                log.info("定时回归触发成功 scheduleId={} runId={} status={}",
                        s.getId(), detail.run().getId(), detail.run().getStatus());
            } catch (Exception e) {
                log.warn("定时回归触发失败 scheduleId={}: {}", s.getId(), e.getMessage());
                resetClaim(s.getId());
            }
        }
    }

    @Override
    public void resetStuckClaims() {
        baseMapper.update(null, new LambdaUpdateWrapper<TestSchedule>()
                .eq(TestSchedule::getLastRunId, CLAIMED)
                .set(TestSchedule::getLastRunId, null));
    }

    /**
     * 独立事务内行锁认领：命中则返回 true 且 {@code lastRunId} 已被置为哨兵 -1（事务提交即释放锁）。
     * 若已被认领 / 未到期 / 已停用，返回 false，不持锁。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Long id) {
        TestSchedule s = baseMapper.selectByIdForUpdate(id);
        if (s == null || !Integer.valueOf(1).equals(s.getEnabled())) {
            return false;
        }
        if (Objects.equals(s.getLastRunId(), CLAIMED)) {
            return false;
        }
        if (!isDue(s)) {
            return false;
        }
        s.setLastRunId(CLAIMED);
        baseMapper.updateById(s);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRun(Long id, Long runId) {
        TestSchedule s = new TestSchedule();
        s.setId(id);
        s.setLastRunId(runId);
        baseMapper.updateById(s);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetClaim(Long id) {
        TestSchedule s = new TestSchedule();
        s.setId(id);
        s.setLastRunId(null);
        baseMapper.updateById(s);
    }

    /** 是否在触发窗口：最近一次执行时间之后的下一个 cron 点已到达 */
    private boolean isDue(TestSchedule s) {
        try {
            CronExpression expr = CronExpression.parse(normalizeCron(s.getCron()));
            LocalDateTime from = lastFireAt(s);
            LocalDateTime next = expr.next(from);
            return next != null && !next.isAfter(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("cron 解析失败 scheduleId={} cron={}: {}", s.getId(), s.getCron(), e.getMessage());
            return false;
        }
    }

    /** 最近一次「触发基准时间」：从未跑→MIN（立即到期）；认领中→now（跳过）；否则取批次 started_at */
    private LocalDateTime lastFireAt(TestSchedule s) {
        Long lid = s.getLastRunId();
        if (lid == null) {
            return LocalDateTime.MIN;
        }
        if (Objects.equals(lid, CLAIMED)) {
            return LocalDateTime.now();
        }
        TestRun run = runMapper.selectById(lid);
        if (run == null) {
            return LocalDateTime.MIN;
        }
        return run.getStartedAt() != null ? run.getStartedAt() : run.getCreateTime();
    }

    private static List<Long> parseSuiteIds(String raw) {
        List<Long> ids = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return ids;
        }
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(p));
            } catch (NumberFormatException ignored) {
                // 跳过非法片段，不阻断其余套件
            }
        }
        return ids;
    }

    /** Spring CronExpression 需 6 段（秒 分 时 日 月 周）；5 段 Unix cron 补前导 0 秒 */
    private static String normalizeCron(String cron) {
        if (cron == null) {
            return cron;
        }
        String trimmed = cron.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return trimmed.chars().filter(ch -> ch == ' ').count() == 4 ? "0 " + trimmed : trimmed;
    }
}
