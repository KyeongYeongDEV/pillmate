class AiServerError(RuntimeError):
    """ai_server 도메인 예외 베이스."""


class LlmInvocationError(AiServerError):
    """LLM 호출 실패."""


class RetrievalError(AiServerError):
    """retrieval 호출 실패."""
