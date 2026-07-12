from __future__ import annotations

import contextvars

"""단일 OCR 요청(asyncio task) 범위의 vision 호출 횟수 누적 — cascade(primary+fallback) 합산 포함.

VisionAdapter 프로토콜 시그니처(extract(image_bytes))를 변경하지 않기 위해
contextvar 로 side-channel 집계한다. service.py 가 요청 시작 시 reset, 완료 후 read.
"""

_attempts: contextvars.ContextVar[int] = contextvars.ContextVar("vision_attempts", default=0)


def reset_attempts() -> None:
    _attempts.set(0)


def record_attempt() -> int:
    n = _attempts.get() + 1
    _attempts.set(n)
    return n


def get_attempts() -> int:
    return _attempts.get()
