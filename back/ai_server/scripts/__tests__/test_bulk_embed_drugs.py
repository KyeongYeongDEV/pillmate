"""
bulk_embed_drugs 단위 테스트 — TDD RED

batch 분할 / checkpoint / failure / _build_text / retry 로직.
DB/OpenAI 호출 없음 (순수 단위).
"""
from __future__ import annotations

import json
import sys
from pathlib import Path
from unittest.mock import MagicMock, patch, call

import pytest

# Scripts 경로를 path에 추가
_SCRIPTS_DIR = Path(__file__).resolve().parent.parent
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))


class TestBuildText:
    def test_name_only(self):
        from bulk_embed_drugs import _build_text
        row = {"name": "타이레놀정500밀리그램", "ingredient": None, "efficacy": None, "dosage": None}
        result = _build_text(row)
        assert result == "타이레놀정500밀리그램"

    def test_name_with_ingredient(self):
        from bulk_embed_drugs import _build_text
        row = {"name": "타이레놀정", "ingredient": "아세트아미노펜500mg", "efficacy": None, "dosage": None}
        result = _build_text(row)
        assert "타이레놀정" in result
        assert "아세트아미노펜500mg" in result

    def test_name_with_efficacy(self):
        from bulk_embed_drugs import _build_text
        row = {"name": "아스피린정", "ingredient": None, "efficacy": "혈전예방", "dosage": None}
        result = _build_text(row)
        assert "아스피린정" in result
        assert "혈전예방" in result

    def test_all_fields(self):
        from bulk_embed_drugs import _build_text
        row = {"name": "메트포르민정", "ingredient": "메트포르민염산염500mg", "efficacy": "혈당강하", "dosage": "1일 2회"}
        result = _build_text(row)
        assert "메트포르민정" in result
        assert "메트포르민염산염500mg" in result
        assert "혈당강하" in result
        assert "1일 2회" in result

    def test_empty_string_ingredient_treated_as_none(self):
        from bulk_embed_drugs import _build_text
        row = {"name": "약이름정", "ingredient": "", "efficacy": None, "dosage": None}
        result = _build_text(row)
        assert result.strip() == "약이름정"


class TestBatchChunks:
    def test_splits_evenly(self):
        from bulk_embed_drugs import _batch_chunks
        items = list(range(10))
        chunks = list(_batch_chunks(items, 3))
        assert chunks == [[0, 1, 2], [3, 4, 5], [6, 7, 8], [9]]

    def test_single_chunk_when_smaller(self):
        from bulk_embed_drugs import _batch_chunks
        items = list(range(5))
        chunks = list(_batch_chunks(items, 100))
        assert chunks == [list(range(5))]

    def test_empty_input(self):
        from bulk_embed_drugs import _batch_chunks
        assert list(_batch_chunks([], 10)) == []


class TestCheckpoint:
    def test_load_returns_default_when_no_file(self, tmp_path):
        from bulk_embed_drugs import _load_checkpoint
        path = tmp_path / "cp.json"
        state = _load_checkpoint(path)
        assert state["last_drug_id"] == 0
        assert state["done_count"] == 0

    def test_save_and_reload(self, tmp_path):
        from bulk_embed_drugs import _load_checkpoint, _save_checkpoint
        path = tmp_path / "cp.json"
        _save_checkpoint({"last_drug_id": 500, "done_count": 100}, path)
        state = _load_checkpoint(path)
        assert state["last_drug_id"] == 500
        assert state["done_count"] == 100


class TestIsRateLimit:
    def test_detects_429(self):
        from bulk_embed_drugs import _is_rate_limit
        assert _is_rate_limit(Exception("HTTP 429 Too Many Requests"))

    def test_detects_rate_word(self):
        from bulk_embed_drugs import _is_rate_limit
        assert _is_rate_limit(Exception("rate limit exceeded"))

    def test_false_for_other_errors(self):
        from bulk_embed_drugs import _is_rate_limit
        assert not _is_rate_limit(Exception("connection refused"))


class TestFormatVector:
    def test_format_produces_valid_pgvector_string(self):
        from bulk_embed_drugs import _format_vector
        v = [1.0, 2.0, 3.0]
        result = _format_vector(v)
        assert result.startswith("[")
        assert result.endswith("]")
        assert "1.0" in result

    def test_format_768_dims(self):
        from bulk_embed_drugs import _format_vector
        v = [0.1] * 768
        result = _format_vector(v)
        assert result.count(",") == 767


class TestEmbedWithRetry:
    def test_succeeds_on_first_try(self):
        from bulk_embed_drugs import _embed_with_retry
        embed_fn = MagicMock(return_value=[[0.1] * 768])
        result = _embed_with_retry(embed_fn, ["test text"])
        assert result == [[0.1] * 768]
        assert embed_fn.call_count == 1

    def test_retries_on_rate_limit_then_succeeds(self):
        from bulk_embed_drugs import _embed_with_retry
        call_count = [0]

        def flaky_embed(texts):
            call_count[0] += 1
            if call_count[0] < 3:
                raise Exception("429 rate limit")
            return [[0.2] * 768]

        with patch("time.sleep"):
            result = _embed_with_retry(flaky_embed, ["test"])
        assert call_count[0] == 3
        assert result == [[0.2] * 768]

    def test_raises_after_max_retries(self):
        from bulk_embed_drugs import _embed_with_retry

        def always_rate_limit(texts):
            raise Exception("429 rate limit exceeded")

        with patch("time.sleep"), pytest.raises(Exception):
            _embed_with_retry(always_rate_limit, ["test"])


class TestDryRunCostEstimate:
    def test_estimate_cost_function_exists(self):
        """dry-run 비용 추정 함수 존재."""
        from bulk_embed_drugs import estimate_cost
        assert callable(estimate_cost)

    def test_estimate_cost_for_100_items(self):
        """100건 * 평균 50 토큰 = 5000 토큰 ~ $0.0001."""
        from bulk_embed_drugs import estimate_cost
        cost = estimate_cost(item_count=100, avg_tokens_per_item=50)
        assert cost < 0.01
        assert cost > 0.0
