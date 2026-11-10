package com.travelmapper.service.llm;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;

/** LLM 调用超时：不应重试，避免把用户等待时间翻倍。 */
public class LlmTimeoutException extends BusinessException {

    public LlmTimeoutException() {
        super(ErrorCode.AI_UNAVAILABLE, "AI 服务超时");
    }
}
