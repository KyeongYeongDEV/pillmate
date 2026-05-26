# PillMate AI Server (Phase 1)

FastAPI + LangChain + Gemini + pgvector 기반 RAG 챗봇.

## 책임
- `POST /api/v1/chat`  의약품 정보 챗봇 (출처 강제, 신뢰도 부족 시 fallback)
- 향후: OCR, 건강 추천, 복용 리포트

## 실행
```bash
cd ai_server
../.venv/bin/python -m uvicorn app.main:app --port 8001 --reload
```

## 테스트
```bash
../.venv/bin/pytest tests/ -v
```
