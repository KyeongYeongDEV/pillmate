class AiServerError(RuntimeError):
    """ai_server 도메인 예외 베이스."""


class LlmInvocationError(AiServerError):
    """LLM 호출 실패."""


class RetrievalError(AiServerError):
    """retrieval 호출 실패."""


class ImageFetchError(AiServerError):
    """presigned URL 이미지 다운로드 실패."""


class VisionInvocationError(AiServerError):
    """Gemini Vision 호출 실패 (네트워크/타임아웃 외)."""


class OcrParseError(AiServerError):
    """LLM 응답 JSON 파싱 실패."""
