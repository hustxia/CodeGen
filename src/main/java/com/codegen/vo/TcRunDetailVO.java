package com.codegen.vo;

import com.codegen.entity.TcArtifact;
import com.codegen.entity.TcCaseResult;
import com.codegen.entity.TcRun;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 运行详情：run + 用例 + 产物 + 已解析的摘要对象。 */
@Data
public class TcRunDetailVO {

    private TcRun run;
    private List<TcCaseResult> cases = new ArrayList<>();
    private List<TcArtifact> artifacts = new ArrayList<>();
    private Object summary;
}
