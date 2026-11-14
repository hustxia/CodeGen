package com.codegen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.codegen.entity.TestEnv;
import com.codegen.vo.TestVO;

/**
 * V9.2 测试环境服务：CRUD 走 MyBatis-Plus，另加一次探活。
 */
public interface TestEnvService extends IService<TestEnv> {

    /** 探活：GET baseUrl，3s 连接超时；返回可达性与状态码 */
    TestVO.EnvCheck verify(Long id);

    /** 下发用 VO：secretRef 脱敏 */
    TestVO.Env toVO(TestEnv entity);
}
