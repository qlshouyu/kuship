package cn.kuship.console.native_;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GraalVM Native Image smoke test。
 *
 * <p>验证 native image 启动 + 基础 endpoint 可调；不要求 100 个 fat-jar 测试用例都跑通
 * （部分用 Mockito 反射 mock，需独立 hint 配置）。
 *
 * <p>触发：{@code mvn -Pnative -Dtest=NativeSmokeTest test}（需 GraalVM 21 community + native-image）
 */
@SpringBootTest(properties = {
        "kuship.security.jwt.secret-key=native-smoke-test-secret-key-must-be-at-least-256-bits-long",
        "spring.aot.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "contract-test"})
class NativeSmokeTest {

    @Autowired MockMvc mvc;

    @Test
    void healthz_returns200() throws Exception {
        mvc.perform(get("/console/healthz"))
                .andExpect(status().isOk());
    }
}
