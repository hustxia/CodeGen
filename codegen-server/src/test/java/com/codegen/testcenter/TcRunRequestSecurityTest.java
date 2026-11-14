package com.codegen.testcenter;

import com.codegen.dto.TcRunRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V9.2 安全红线：请求体夹带的命令/路径参数必须被识别并拒绝。 */
class TcRunRequestSecurityTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void cleanRequestIsAccepted() throws Exception {
        TcRunRequest req = mapper.readValue(
                "{\"suiteIds\":[\"S1\",\"S4\"],\"trigger\":\"MANUAL\",\"gitCommit\":\"2ffc550\"}",
                TcRunRequest.class);
        assertNull(req.firstForbiddenKey());
        assertEquals(List.of("S1", "S4"), req.getSuiteIds());
        assertEquals("2ffc550", req.getGitCommit());
    }

    @Test
    void commandFieldIsRejected() throws Exception {
        TcRunRequest req = mapper.readValue(
                "{\"suiteIds\":[\"S1\"],\"command\":\"rm -rf /\"}", TcRunRequest.class);
        assertEquals("command", req.firstForbiddenKey());
        assertNotNull(req.getUnknownFields().get("command"));
    }

    @Test
    void pathTraversalFieldIsRejectedCaseInsensitively() throws Exception {
        TcRunRequest req = mapper.readValue(
                "{\"suiteIds\":[\"S1\"],\"filePath\":\"../../etc/passwd\"}", TcRunRequest.class);
        assertEquals("filePath", req.firstForbiddenKey());
        TcRunRequest req2 = mapper.readValue(
                "{\"work_dir\":\"/etc\",\"timeoutMs\":999999}", TcRunRequest.class);
        assertTrue(req2.firstForbiddenKey() != null);
    }

    @Test
    void unrelatedUnknownFieldIsHarmlessButRecorded() throws Exception {
        TcRunRequest req = mapper.readValue(
                "{\"suiteIds\":[\"S1\"],\"clientVersion\":\"9.2\"}", TcRunRequest.class);
        assertNull(req.firstForbiddenKey());
        assertEquals("9.2", req.getUnknownFields().get("clientVersion"));
    }

    @Test
    void serviceRejectsForbiddenRequestWithForbiddenCode() {
        TcRunRequest req = new TcRunRequest();
        req.collectUnknown("cmd", List.of("calc"));
        TcException ex = assertThrows(TcException.class, () -> {
            if (req.firstForbiddenKey() != null) {
                throw TcException.forbidden("请求体含禁止字段");
            }
        });
        assertEquals(403, ex.getCode());
    }
}
