---
name: rag-eval
description: RAG 챗봇과 OCR 매칭의 정확도를 RAGAS 지표로 평가하는 워크플로우.
---

# RAG Evaluation

## 평가 지표

| 지표 | 정의 | 목표 |
|------|------|------|
| **Faithfulness** | 응답이 검색 청크에 근거하는가 | >= 0.95 (의료 도메인) |
| **Answer Relevancy** | 응답이 질문에 답하는가 | >= 0.90 |
| **Context Precision** | 검색 청크의 정밀도 | >= 0.85 |
| **Context Recall** | 정답이 검색에 포함된 비율 | >= 0.90 |
| **MRR@10** | Mean Reciprocal Rank | >= 0.80 |

## 워크플로우

### 1. 평가 데이터셋 구축
- [ ] 한국 의료 도메인 질문 100개 작성
- [ ] 각 질문에 정답 청크 ID + 정답 답변 명시
- [ ] 5개 카테고리: 효능, 부작용, 병용금기, 용량, 보관

### 2. 평가 실행
```bash
python scripts/eval_rag.py --dataset eval/medical-qa-100.jsonl --output reports/rag-eval-{date}.json
```

### 3. 회귀 방지
- [ ] CI에서 Faithfulness < 0.95 → 빌드 실패
- [ ] 주 1회 평가 자동화 (cron)
- [ ] 지표 변화 추적 (Grafana 대시보드)

## 의료 도메인 추가 검증

| 검증 | 기준 |
|------|------|
| 식약처 출처 포함률 | 100% |
| 환각 발생률 | 0% (수동 샘플링) |
| 병용금기 누락률 | 0% |
| "확인 불가" fallback 적절성 | 검토 필요 케이스 100% 발동 |

## 실패 시 액션

| 지표 저하 | 조치 |
|-----------|------|
| Faithfulness ↓ | 프롬프트에 "검색 결과 외 답변 금지" 강화 |
| Context Recall ↓ | 청크 전략 재검토, K 증가 |
| Context Precision ↓ | 청크 메타데이터 필터링 추가 |
| MRR ↓ | Hybrid Retrieval 가중치 조정 |

## 참조

- `agents/rag-curator.md`: RAG 큐레이터
- `agents/medical-domain-validator.md`: 의료 검증
- `scripts/eval_rag.py`: 평가 스크립트
