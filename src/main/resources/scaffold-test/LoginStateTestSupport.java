package __PKG_;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * V6.7 集成测试基类：H2 内存库 + 真实鉴权链。
 *
 * <p>登录态不走 BCrypt 校验（那是登录接口的职责），直接向 sys_session 插一条
 * 一小时后过期的会话——与线上 TokenStore 签发的落盘行为完全同构，
 * AuthContextFilter 查库/滑动续期全链路都是真的。
 *
 * <p>表结构由 src/test/resources/schema/base.sql（基座）与 schema/table-*.sql（业务表，
 * 代码生成时逐张落盘）在 Spring SQL init 阶段装载，无外部 MySQL/Docker 依赖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class LoginStateTestSupport {

    /** 每个测试类插一条独立会话（token 随机，互不冲突；类内共享减少重复插入） */
    private static String token;

    @Autowired
    protected MockMvc mockMvc;

    @BeforeAll
    static void injectLoginSession(@Autowired JdbcTemplate jdbcTemplate) {
        token = "it-" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
                "INSERT INTO sys_session (token, username, login_ip, expire_time) VALUES (?, ?, ?, ?)",
                token, "admin", "127.0.0.1", LocalDateTime.now().plusHours(1));
    }

    /** 鉴权头：与前端 utils/request.js 的 Authorization: Bearer 约定一致 */
    protected static String bearer() {
        return "Bearer " + token;
    }
}
