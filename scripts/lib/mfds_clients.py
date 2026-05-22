"""식약처 3 API 클라이언트.

- 1) e약은요  : DrbEasyDrugInfoService/getDrbEasyDrugList
- 2) 낱알식별 : MdcinGrnIdntfcInfoService03/getMdcinGrnIdntfcInfoList03
- 3) 제품허가 : DrugPrdtPrmsnInfoService07/getDrugPrdtPrmsnInq07

각 함수는 (items, totalCount) 를 반환한다.
- type=json 우선, JSON 파싱 실패 시 XML(xmltodict) fallback.
- 5xx HTTPError: 지수 백오프 3회 (1s/2s/4s).
- 4xx: 즉시 raise.
"""
from __future__ import annotations

import json
import logging
import time
from typing import Any

import httpx
import xmltodict

logger = logging.getLogger(__name__)

BASE_URL = "https://apis.data.go.kr/1471000"

EASY_PATH = "/DrbEasyDrugInfoService/getDrbEasyDrugList"
IDENT_PATH = "/MdcinGrnIdntfcInfoService03/getMdcinGrnIdntfcInfoList03"
PERMIT_PATH = "/DrugPrdtPrmsnInfoService07/getDrugPrdtPrmsnInq07"

_TIMEOUT = httpx.Timeout(connect=10.0, read=30.0, write=10.0, pool=10.0)


class MfdsClientError(RuntimeError):
    """식약처 API 호출 실패."""


def _coerce_items(node: Any) -> list[dict[str, Any]]:
    """response.body.items 정규화 (단일 dict 도 list 로)."""
    if node is None:
        return []
    if isinstance(node, list):
        return [x for x in node if isinstance(x, dict)]
    if isinstance(node, dict):
        # XML 변환 시 items 가 {'item': [...]} 일 수 있음
        if "item" in node:
            sub = node["item"]
            if isinstance(sub, list):
                return [x for x in sub if isinstance(x, dict)]
            if isinstance(sub, dict):
                return [sub]
            return []
        return [node]
    return []


def _coerce_total(node: Any) -> int:
    try:
        return int(node)
    except (TypeError, ValueError):
        return 0


def _parse_payload(text: str, content_type: str) -> dict[str, Any]:
    """JSON 우선, 실패 시 XML 파싱."""
    ct = (content_type or "").lower()
    if "json" in ct:
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            logger.warning("expected JSON but parse failed, falling back to XML")
    else:
        try:
            return json.loads(text)
        except (json.JSONDecodeError, ValueError):
            pass
    # XML fallback
    parsed = xmltodict.parse(text)
    return parsed


def _extract_body(payload: dict[str, Any]) -> tuple[list[dict[str, Any]], int, str | None]:
    """식약처 표준 응답에서 (items, totalCount, errMsg) 추출."""
    response = payload.get("response") or payload
    header = response.get("header", {}) if isinstance(response, dict) else {}
    result_code = header.get("resultCode") if isinstance(header, dict) else None
    err_msg = None
    if result_code not in (None, "00", "0"):
        err_msg = f"resultCode={result_code} msg={header.get('resultMsg')}"

    body = response.get("body", {}) if isinstance(response, dict) else {}
    if not isinstance(body, dict):
        body = {}
    items_node = body.get("items")
    items = _coerce_items(items_node)
    total = _coerce_total(body.get("totalCount"))
    return items, total, err_msg


def _request_with_retry(url: str, params: dict[str, Any]) -> dict[str, Any]:
    delays = [1.0, 2.0, 4.0]
    last_exc: Exception | None = None
    for attempt in range(len(delays) + 1):
        try:
            with httpx.Client(timeout=_TIMEOUT) as client:
                resp = client.get(url, params=params)
            if 500 <= resp.status_code < 600:
                raise httpx.HTTPStatusError(
                    f"5xx server error {resp.status_code}", request=resp.request, response=resp
                )
            if 400 <= resp.status_code < 500:
                # 4xx 즉시 실패
                raise MfdsClientError(
                    f"4xx client error {resp.status_code} for {url}: {resp.text[:200]}"
                )
            text = resp.text
            ct = resp.headers.get("content-type", "")
            payload = _parse_payload(text, ct)
            return payload
        except (httpx.TimeoutException, httpx.NetworkError, httpx.HTTPStatusError) as exc:
            last_exc = exc
            if attempt >= len(delays):
                break
            sleep_for = delays[attempt]
            logger.warning(
                "MFDS call failed (%s), retrying in %.1fs (attempt %d/%d)",
                exc,
                sleep_for,
                attempt + 1,
                len(delays),
            )
            time.sleep(sleep_for)
    raise MfdsClientError(f"exhausted retries: {last_exc}") from last_exc


def _fetch(api_key: str, path: str, page: int, num: int) -> tuple[list[dict[str, Any]], int]:
    url = f"{BASE_URL}{path}"
    params = {
        "serviceKey": api_key,
        "pageNo": page,
        "numOfRows": num,
        "type": "json",
    }
    payload = _request_with_retry(url, params)
    items, total, err = _extract_body(payload)
    if err:
        # 빈 결과 + 에러코드면 raise. 정상 빈 결과(03) 는 무시.
        # 식약처: 00=정상, 03=NODATA_ERROR (정상 종료로 간주)
        if "03" not in err:
            raise MfdsClientError(err)
    return items, total


def fetch_easy(api_key: str, page: int, num: int) -> tuple[list[dict[str, Any]], int]:
    """e약은요 1페이지를 가져온다."""
    return _fetch(api_key, EASY_PATH, page, num)


def fetch_ident(api_key: str, page: int, num: int) -> tuple[list[dict[str, Any]], int]:
    """낱알식별 1페이지를 가져온다."""
    return _fetch(api_key, IDENT_PATH, page, num)


def fetch_permit(api_key: str, page: int, num: int) -> tuple[list[dict[str, Any]], int]:
    """제품허가 1페이지를 가져온다."""
    return _fetch(api_key, PERMIT_PATH, page, num)
