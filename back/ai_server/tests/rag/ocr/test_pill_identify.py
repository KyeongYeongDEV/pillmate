"""
PillIdentifyAdapter TDD RED — T-AI-OCR-PILL-IDENTIFY-FALLBACK

낱알식별 (shape/color/mark) → DB 매칭
DB V6 마이그레이션 컬럼: shape_class / color_class / mark_code_front / mark_code_back
"""
from __future__ import annotations

from decimal import Decimal
from unittest.mock import AsyncMock, MagicMock, patch

import pytest


def _mock_pool_with_rows(rows: list[dict]) -> MagicMock:
    mock_conn = AsyncMock()
    mock_conn.fetch.return_value = [MagicMock(**r) for r in rows]
    mock_conn.fetch.return_value = rows  # asyncpg returns Record-like objects

    pool = MagicMock()
    pool.acquire.return_value.__aenter__ = AsyncMock(return_value=mock_conn)
    pool.acquire.return_value.__aexit__ = AsyncMock(return_value=False)
    return pool, mock_conn


class TestPillAppearance:
    def test_pill_appearance_importable(self):
        """PillAppearance 클래스 존재 확인."""
        from app.domain.pill_appearance import PillAppearance

        assert PillAppearance is not None

    def test_pill_appearance_default_all_none(self):
        """모든 필드 기본값 None."""
        from app.domain.pill_appearance import PillAppearance

        a = PillAppearance()
        assert a.shape is None
        assert a.color is None
        assert a.mark_front is None
        assert a.mark_back is None
        assert a.line is None

    def test_pill_appearance_accepts_fields(self):
        """필드 지정 생성."""
        from app.domain.pill_appearance import PillAppearance

        a = PillAppearance(shape="원형", color="하양", mark_front="T", line=True)
        assert a.shape == "원형"
        assert a.color == "하양"
        assert a.mark_front == "T"
        assert a.line is True


class TestRawOcrItemAppearance:
    def test_raw_ocr_item_has_appearance_field(self):
        """RawOcrItem 에 appearance: PillAppearance | None 필드 추가 확인."""
        from app.domain.ocr import RawOcrItem

        item = RawOcrItem(name_raw="타이레놀정", confidence=Decimal("0.9"))
        assert hasattr(item, "appearance")
        assert item.appearance is None

    def test_raw_ocr_item_accepts_appearance(self):
        """RawOcrItem 에 PillAppearance 지정 가능."""
        from app.domain.ocr import RawOcrItem
        from app.domain.pill_appearance import PillAppearance

        item = RawOcrItem(
            name_raw="타이레놀정",
            confidence=Decimal("0.9"),
            appearance=PillAppearance(shape="원형", color="하양"),
        )
        assert item.appearance.shape == "원형"


class TestPillIdentifyAdapter:
    def test_pill_identify_adapter_importable(self):
        """PillIdentifyAdapter 존재 확인."""
        from app.rag.ocr.pill_identify import PillIdentifyAdapter

        assert PillIdentifyAdapter is not None

    def test_pill_identify_adapter_has_identify_method(self):
        """identify(appearance) 비동기 메서드 존재."""
        import inspect
        from app.rag.ocr.pill_identify import PillIdentifyAdapter

        assert hasattr(PillIdentifyAdapter, "identify")
        assert inspect.iscoroutinefunction(PillIdentifyAdapter.identify)

    @pytest.mark.asyncio
    async def test_identify_returns_empty_when_no_shape(self):
        """shape 없으면 DB 호출 없이 빈 리스트 반환."""
        from app.domain.pill_appearance import PillAppearance
        from app.rag.ocr.pill_identify import PillIdentifyAdapter

        pool = MagicMock()
        adapter = PillIdentifyAdapter(pool=pool)
        result = await adapter.identify(PillAppearance())

        assert result == []
        assert not pool.acquire.called

    @pytest.mark.asyncio
    async def test_identify_returns_matches_for_shape_and_color(self):
        """shape + color 일치 → MatchCandidate 반환."""
        from app.domain.pill_appearance import PillAppearance
        from app.rag.ocr.pill_identify import PillIdentifyAdapter

        pool, mock_conn = _mock_pool_with_rows([
            {"kd_code": "200400001", "name": "타이레놀정500밀리그램"},
        ])
        adapter = PillIdentifyAdapter(pool=pool)
        result = await adapter.identify(PillAppearance(shape="원형", color="하양"))

        assert len(result) > 0
        assert result[0].kd_code == "200400001"
        assert result[0].name == "타이레놀정500밀리그램"

    @pytest.mark.asyncio
    async def test_identify_returns_empty_when_no_db_rows(self):
        """DB 결과 없으면 빈 리스트 반환."""
        from app.domain.pill_appearance import PillAppearance
        from app.rag.ocr.pill_identify import PillIdentifyAdapter

        pool, _ = _mock_pool_with_rows([])
        adapter = PillIdentifyAdapter(pool=pool)
        result = await adapter.identify(PillAppearance(shape="원형", color="하양"))

        assert result == []

    @pytest.mark.asyncio
    async def test_identify_with_mark_calls_db_with_mark_param(self):
        """mark_front 있으면 mark 포함 SQL 파라미터 전달."""
        from app.domain.pill_appearance import PillAppearance
        from app.rag.ocr.pill_identify import PillIdentifyAdapter

        pool, mock_conn = _mock_pool_with_rows([
            {"kd_code": "ABC123", "name": "마크약정"},
        ])
        adapter = PillIdentifyAdapter(pool=pool)
        result = await adapter.identify(
            PillAppearance(shape="타원형", color="파랑", mark_front="T")
        )

        assert mock_conn.fetch.called
        call_args = mock_conn.fetch.call_args
        # mark param 이 '%T%' 형태로 전달됐는지 확인
        params = call_args[0][1:]  # (sql, param1, param2, ...)
        mark_param_present = any("T" in str(p) for p in params if p is not None)
        assert mark_param_present

    @pytest.mark.asyncio
    async def test_identify_db_exception_returns_empty(self):
        """DB 오류 → 빈 리스트 반환 (cascade 계속)."""
        from app.domain.pill_appearance import PillAppearance
        from app.rag.ocr.pill_identify import PillIdentifyAdapter

        mock_conn = AsyncMock()
        mock_conn.fetch.side_effect = Exception("DB error")
        pool = MagicMock()
        pool.acquire.return_value.__aenter__ = AsyncMock(return_value=mock_conn)
        pool.acquire.return_value.__aexit__ = AsyncMock(return_value=False)

        adapter = PillIdentifyAdapter(pool=pool)
        result = await adapter.identify(PillAppearance(shape="원형", color="하양"))

        assert result == []

    def test_sql_uses_explicit_type_casts_for_nullable_params(self):
        """AmbiguousParameterError 회귀 방지 — $n IS NULL 파라미터에 ::text/::int 캐스트 강제."""
        from app.rag.ocr.pill_identify import _SQL

        assert "$2::text IS NULL" in _SQL
        assert "$3::text IS NULL" in _SQL
        assert "LIMIT $4::int" in _SQL
        # 캐스트 없는 bare 'IS NULL' 패턴이 남아있지 않아야 함
        assert "$2 IS NULL" not in _SQL
        assert "$3 IS NULL" not in _SQL

    @pytest.mark.asyncio
    async def test_identify_repeated_calls_safe(self):
        """동일 입력 반복 호출 안전 — 매 호출 정상 결과."""
        from app.domain.pill_appearance import PillAppearance
        from app.rag.ocr.pill_identify import PillIdentifyAdapter

        pool, _ = _mock_pool_with_rows([{"kd_code": "200400001", "name": "타이레놀정"}])
        adapter = PillIdentifyAdapter(pool=pool)
        appearance = PillAppearance(shape="원형", color="하양", mark_front="T")

        first = await adapter.identify(appearance)
        second = await adapter.identify(appearance)

        assert first[0].kd_code == second[0].kd_code == "200400001"

    @pytest.mark.asyncio
    async def test_identify_color_none_passes_null_param(self):
        """color 없으면 color param=None (SQL $2 IS NULL 분기) — mark만으로 조회."""
        from app.domain.pill_appearance import PillAppearance
        from app.rag.ocr.pill_identify import PillIdentifyAdapter

        pool, mock_conn = _mock_pool_with_rows([])
        adapter = PillIdentifyAdapter(pool=pool)
        await adapter.identify(PillAppearance(shape="원형"))

        params = mock_conn.fetch.call_args[0][1:]  # (sql, shape, color, mark, limit)
        assert params[0] == "원형"
        assert params[1] is None  # color_param
        assert params[2] is None  # mark_param

    @pytest.mark.asyncio
    async def test_identify_returns_match_candidates_with_score(self):
        """반환 타입이 MatchCandidate 이고 score 필드 존재."""
        from decimal import Decimal
        from app.domain.pill_appearance import PillAppearance
        from app.rag.ocr.matcher import MatchCandidate
        from app.rag.ocr.pill_identify import PillIdentifyAdapter

        pool, _ = _mock_pool_with_rows([
            {"kd_code": "XYZ001", "name": "테스트정"},
        ])
        adapter = PillIdentifyAdapter(pool=pool)
        result = await adapter.identify(PillAppearance(shape="원형"))

        assert all(isinstance(c, MatchCandidate) for c in result)
        assert all(isinstance(c.score, Decimal) for c in result)
