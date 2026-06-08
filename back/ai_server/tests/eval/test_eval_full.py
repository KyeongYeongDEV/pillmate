"""
EvalFullRunner 단위 테스트 — TDD RED → GREEN

순수 함수 (_auto_inn, _is_hit_by_inn, _get_inn) + EvalFullRunner 로직 검증.
실제 DB 호출 없는 단위 테스트 (DB 연결 테스트는 pytest -m eval_full).
"""
from __future__ import annotations

import pytest


class TestAutoInn:
    def test_extracts_first_4_korean_chars(self):
        from tests.eval.run_eval_full import _auto_inn
        assert _auto_inn("타이레놀정500밀리그람(아세트아미노펜)") == "타이레놀"

    def test_caps_at_4_chars(self):
        from tests.eval.run_eval_full import _auto_inn
        result = _auto_inn("암로디핀베실산염정5밀리그램")
        assert len(result) == 4
        assert result == "암로디핀"

    def test_short_name_returns_all(self):
        from tests.eval.run_eval_full import _auto_inn
        assert _auto_inn("아스피린") == "아스피린"

    def test_non_korean_falls_back_to_prefix(self):
        from tests.eval.run_eval_full import _auto_inn
        result = _auto_inn("Tylenol 500")
        assert isinstance(result, str)
        assert len(result) > 0


class TestIsHitByInn:
    def test_returns_true_when_inn_in_matched(self):
        from tests.eval.run_eval_full import _is_hit_by_inn
        assert _is_hit_by_inn("타이레놀정500밀리그람(아세트아미노펜)", "타이레놀")

    def test_returns_false_when_inn_not_in_matched(self):
        from tests.eval.run_eval_full import _is_hit_by_inn
        assert not _is_hit_by_inn("아스피린정100mg", "타이레놀")

    def test_returns_false_for_none_matched(self):
        from tests.eval.run_eval_full import _is_hit_by_inn
        assert not _is_hit_by_inn(None, "타이레놀")

    def test_returns_false_for_empty_inn(self):
        from tests.eval.run_eval_full import _is_hit_by_inn
        assert not _is_hit_by_inn("타이레놀정", "")


class TestGetInn:
    def test_uses_hard_inn_map(self):
        from tests.eval.run_eval_full import _get_inn
        result = _get_inn("gt_031", "항히스타민제")
        assert result == "세티리진"

    def test_uses_medium_inn_map(self):
        from tests.eval.run_eval_full import _get_inn
        result = _get_inn("gt_017", "타이레놀정500밀리그램")
        assert result == "타이레놀"

    def test_falls_back_to_auto_for_easy(self):
        from tests.eval.run_eval_full import _get_inn
        result = _get_inn("gt_001", "타이레놀정500밀리그람(아세트아미노펜)")
        assert result == "타이레놀"

    def test_falls_back_to_auto_for_unknown_id(self):
        from tests.eval.run_eval_full import _get_inn
        result = _get_inn("gt_999", "독시사이클린캡슐100밀리그램")
        assert result == "독시사이"


class TestEvalFullRunnerUnit:
    def test_runner_class_exists(self):
        from tests.eval.run_eval_full import EvalFullRunner
        assert EvalFullRunner is not None

    def test_runner_is_instantiable_with_mock_pool(self):
        from tests.eval.run_eval_full import EvalFullRunner

        class MockPool:
            pass

        runner = EvalFullRunner(MockPool())
        assert runner is not None
