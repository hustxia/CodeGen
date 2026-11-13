package com.codegen.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codegen.entity.TestSuite;
import com.codegen.mapper.TestSuiteMapper;
import com.codegen.service.TestSuiteService;
import org.springframework.stereotype.Service;

/**
 * V9.2 测试套件实现：纯 CRUD。
 */
@Service
public class TestSuiteServiceImpl extends ServiceImpl<TestSuiteMapper, TestSuite> implements TestSuiteService {
}
