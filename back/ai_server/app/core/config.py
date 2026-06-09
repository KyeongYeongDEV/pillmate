from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(".env", "../.env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    gemini_api_key: str = Field(default="", alias="GEMINI_API_KEY")
    gemini_model: str = Field(default="gemini-2.5-flash")
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

    retrieval_top_k: int = Field(default=5)
    faithfulness_threshold: float = Field(default=0.95)
    reranker_enabled: bool = Field(default=False, alias="RERANKER_ENABLED")

    # OCR 4-Tier fallback feature flags
    ocr_correction_enabled: bool = Field(default=True, alias="OCR_CORRECTION_ENABLED")
    ocr_grounding_enabled: bool = Field(default=False, alias="OCR_GROUNDING_ENABLED")

    # Phase B-6: 이미지 전처리 / Few-shot 프롬프트 feature flags
    ocr_preprocess_enabled: bool = Field(default=True, alias="PREPROCESS_ENABLED")
    ocr_fewshot_enabled: bool = Field(default=True, alias="FEWSHOT_ENABLED")


def get_settings() -> Settings:
    return Settings()
