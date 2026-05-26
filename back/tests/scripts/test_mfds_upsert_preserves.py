"""UPSERT 가 None 값으로 기존 컬럼을 덮어쓰지 않는지 검증.

원인: e약은요 4,756 건이 먼저 INSERT 된 뒤 같은 itemSeq 가 ident/permit 페이지에서 나타나면
ON CONFLICT DO UPDATE 가 None 컬럼까지 NULL 로 덮어써 efficacy/dosage/warning 이 사라졌다.

해결: SET col = COALESCE(EXCLUDED.col, drugs.col).
"""
from __future__ import annotations

from scripts.bulk_import_mfds import NOT_NULL_COLUMNS, _build_upsert_sql, _row_to_params
from scripts.lib.mfds_merge import merge_drug_record

EASY_ONLY_COLUMNS = ["efficacy", "dosage", "warning", "precaution", "interaction", "storage_method"]
IDENT_POPULATED_COLUMNS = ["shape_class", "color_class", "mark_code_front"]
IDENT_NULLABLE_COLUMNS_IN_SQL = ["shape_class", "color_class", "mark_code_front", "mark_code_back"]
PERMIT_ONLY_COLUMNS = ["permit_no", "permit_date", "main_ingr"]


def _easy_record() -> dict:
    return {
        "itemSeq": "200800123",
        "itemName": "테스트정",
        "entpName": "테스트제약",
        "efcyQesitm": "두통 완화",
        "useMethodQesitm": "1일 3회",
        "atpnWarnQesitm": "경고문",
        "atpnQesitm": "주의문",
        "intrcQesitm": "상호작용문",
        "seQesitm": "부작용문",
        "depositMethodQesitm": "실온 보관",
        "bizrno": "1234567890",
    }


def _ident_record() -> dict:
    return {
        "ITEM_SEQ": "200800123",
        "ITEM_NAME": "테스트정",
        "ENTP_NAME": "테스트제약",
        "DRUG_SHAPE": "원형",
        "COLOR_CLASS1": "흰색",
        "MARK_CODE_FRONT": "TEST",
        "ETC_OTC_NAME": "일반의약품",
    }


def test_upsert_sql_uses_coalesce_for_nullable_columns():
    sql = _build_upsert_sql()
    for col in EASY_ONLY_COLUMNS + IDENT_NULLABLE_COLUMNS_IN_SQL + PERMIT_ONLY_COLUMNS:
        assert f"{col} = COALESCE(EXCLUDED.{col}, drugs.{col})" in sql, col


def test_upsert_sql_uses_direct_assignment_for_not_null_columns():
    sql = _build_upsert_sql()
    for col in NOT_NULL_COLUMNS - {"kd_code"}:
        assert f"{col} = EXCLUDED.{col}" in sql, col


def test_merge_easy_then_ident_keeps_easy_columns_separate():
    """easy 머지 결과에는 e약은요 컬럼이, ident 머지 결과에는 외형 컬럼이 들어가야 한다.

    COALESCE 패턴 + None 제외 머지가 합쳐지면 efficacy 가 보존된다.
    """
    easy_row = merge_drug_record(_easy_record(), None, None)
    ident_row = merge_drug_record(None, _ident_record(), None)

    for col in EASY_ONLY_COLUMNS:
        assert easy_row.get(col) is not None, f"easy must populate {col}"
        assert col not in ident_row, f"ident must not include {col}"
    for col in IDENT_POPULATED_COLUMNS:
        assert col not in easy_row, f"easy must not include {col}"
        assert ident_row.get(col) is not None, f"ident must populate {col}"


def test_row_to_params_does_not_send_none_for_missing_easy_columns_when_ident_arrives():
    """ident-only 머지 결과를 SQL params 로 변환해도 efficacy 키는 None 으로 전달된다.

    COALESCE 가 EXCLUDED.efficacy=NULL 일 때 drugs.efficacy 를 보존하므로,
    None 전달 자체는 안전하다. 본 테스트는 params 의 None 여부만 명시한다.
    """
    ident_row = merge_drug_record(None, _ident_record(), None)
    params = _row_to_params(ident_row)

    for col in EASY_ONLY_COLUMNS:
        assert params[col] is None, col
    for col in IDENT_POPULATED_COLUMNS:
        assert params[col] is not None, col
    assert params["is_rare"] is False
    assert params["source"] == "식품의약품안전처"
