package com.codegen.vo;

import com.codegen.entity.GenClarifyQuestion;
import com.codegen.entity.GenClarifySession;
import lombok.Data;

/** V9.25 一次一问的返回：会话 + 当前问题（无下一问时 question=null） */
@Data
public class ClarifyStepVO {

    private GenClarifySession session;
    private GenClarifyQuestion question;
    private boolean done;
    private int answeredCount;
    private int totalQuestions;
}
