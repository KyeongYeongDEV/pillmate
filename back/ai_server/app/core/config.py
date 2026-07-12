from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(".env", "../.env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    gemini_api_key: str = Field(default="", alias="GEMINI_API_KEY")
    gemini_model: str = Field(default="gemini-2.5-flash-lite")

    @property
    def gemini_key_list(self) -> list[str]:
        """빈 키 필터 — 빈 리스트면 GeminiInvoker 가 RuntimeError 로 부팅 조기 실패 (fail-fast)."""
        return [k for k in [self.gemini_api_key] if k]

    openai_api_key: str = Field(default="", alias="OPENAI_API_KEY")
    openai_api_key_alt: str = Field(default="", alias="OpenAI_API_KEY")
    embedding_model: str = Field(default="text-embedding-3-small")
    embedding_dim: int = Field(default=768)

    @property
    def effective_openai_key(self) -> str:
        return self.openai_api_key or self.openai_api_key_alt

    postgres_host: str = Field(default="localhost", alias="POSTGRES_HOST")
    postgres_port: int = Field(default=5433, alias="POSTGRES_PORT")
    postgres_db: str = Field(default="pillmate", alias="POSTGRES_DB")
    postgres_user: str = Field(default="pillmate", alias="POSTGRES_USER")
    postgres_password: str = Field(default="", alias="POSTGRES_PASSWORD")

    redis_host: str = Field(default="localhost", alias="REDIS_HOST")
    redis_port: int = Field(default=6379, alias="REDIS_PORT")

    retrieval_top_k: int = Field(default=5)
    faithfulness_threshold: float = Field(default=0.95)
    reranker_enabled: bool = Field(default=False, alias="RERANKER_ENABLED")

    # OCR 4-Tier fallback feature flags
    ocr_correction_enabled: bool = Field(default=True, alias="OCR_CORRECTION_ENABLED")
    ocr_grounding_enabled: bool = Field(default=False, alias="OCR_GROUNDING_ENABLED")

    # Phase B-6: 이미지 전처리 / Few-shot 프롬프트 feature flags
    ocr_preprocess_enabled: bool = Field(default=True, alias="PREPROCESS_ENABLED")
    ocr_fewshot_enabled: bool = Field(default=True, alias="FEWSHOT_ENABLED")

    # Phase B-7: 낱알식별 fallback (Tier 5) — 기본 비활성 (2026-07-11 사용자 결정).
    # 실측 근거: OcrProcessed total_elapsed_ms 병목 조사 중 이름 미인식 약 다건 발생 시
    # 지연 원인으로 지목되어 기본 OFF. 코드는 유지 — 필요 시 플래그만 켜면 재활성.
    pill_identify_enabled: bool = Field(default=False, alias="PILL_IDENTIFY_ENABLED")

    # Phase C-1: 매처 구현 선택 (rrf | legacy)
    drug_matcher_impl: str = Field(default="rrf", alias="DRUG_MATCHER_IMPL")

    # OCR vision adapter variant 선택 (auto | flash | lite). auto = gemini_model 이름 추론
    ocr_vision_variant: str = Field(default="auto", alias="OCR_VISION_VARIANT")

    # 에러 추적 — DSN 빈값이면 Sentry 비활성 (로컬 OFF)
    sentry_dsn: str = Field(default="", alias="SENTRY_DSN")
    environment: str = Field(default="local", alias="SPRING_PROFILES_ACTIVE")


def get_settings() -> Settings:
    return Settings()
