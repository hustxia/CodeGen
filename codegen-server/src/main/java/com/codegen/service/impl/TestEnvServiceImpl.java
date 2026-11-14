package com.codegen.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codegen.common.exception.BusinessException;
import com.codegen.entity.TestEnv;
import com.codegen.mapper.TestEnvMapper;
import com.codegen.service.TestEnvService;
import com.codegen.support.ApiTestExecutor;
import com.codegen.vo.TestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * V9.2 测试环境实现。
 *
 * <p>探活走一次空路径 GET：能拿到任何 HTTP 状态码都算「可达」，
 * 只有连接失败/超时才判定不可达——401/404 说明服务活着，只是路径没配对。
 */
@Service
public class TestEnvServiceImpl extends ServiceImpl<TestEnvMapper, TestEnv> implements TestEnvService {

    private static final String MASK = "******";

    @Autowired
    private ApiTestExecutor executor;

    @Override
    public TestVO.EnvCheck verify(Long id) {
        TestEnv env = getById(id);
        if (env == null) {
            throw new BusinessException(400, "环境不存在");
        }
        ApiTestExecutor.ExecResult r = executor.execute(env.getBaseUrl(), "GET", "", null, null);
        if (!r.ok()) {
            return new TestVO.EnvCheck(false, -1, r.durationMs(), r.error());
        }
        return new TestVO.EnvCheck(true, r.status(), r.durationMs(), "HTTP " + r.status());
    }

    @Override
    public TestVO.Env toVO(TestEnv e) {
        if (e == null) {
            return null;
        }
        String ref = e.getSecretRef() == null || e.getSecretRef().isBlank() ? null : MASK;
        return new TestVO.Env(e.getId(), e.getProjectId(), e.getName(), e.getBaseUrl(),
                e.getHeadersJson(), ref, e.getIsProd(), e.getEnabled(), e.getRemark());
    }
}
