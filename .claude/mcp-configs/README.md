---
name: mcp-configs
description: PillMate MCP 서버 설정 모음
---

# PillMate MCP Configs

PillMate에서 사용하는 외부 시스템 연동 설정입니다.

## 파일 목록

| 파일 | 용도 |
|------|------|
| `gemini.json` | Gemini Vision / Text 모델 설정 |
| `mfds-api.json` | 식약처 의약품 API |
| `aws-s3.json` | S3 처방전 이미지 버킷 |
| `postgres.json` | PostgreSQL + pgvector |

## 비밀 키

API 키는 이 파일에 **포함하지 않는다**. 환경변수 또는 AWS Secrets Manager.

각 파일에는 `env_var` 키로 환경변수 이름만 명시.

## 예시

```json
{
  "api_key_env": "MFDS_API_KEY",
  "base_url": "https://apis.data.go.kr/..."
}
```
