from __future__ import annotations

import asyncio
import logging

from fastapi import APIRouter, Depends, HTTPException

from app.domain.ocr import PrescriptionOcrRequest, PrescriptionOcrResponse
from app.exceptions import ImageFetchError, OcrParseError, VisionBusyError, VisionInvocationError
from app.rag.ocr.service import OcrPrescriptionService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1")


def get_ocr_service() -> OcrPrescriptionService:
    raise RuntimeError("OcrPrescriptionService dependency must be overridden at app startup")


@router.post("/ocr/prescription", response_model=PrescriptionOcrResponse)
async def ocr_prescription(
    request: PrescriptionOcrRequest,
    service: OcrPrescriptionService = Depends(get_ocr_service),
) -> PrescriptionOcrResponse:
    try:
        return await service.process(request)
    except ImageFetchError:
        raise HTTPException(status_code=502, detail={"code": "OCR_001", "message": "presigned 이미지 다운로드 실패"})
    except asyncio.TimeoutError:
        raise HTTPException(status_code=504, detail={"code": "OCR_002", "message": "Gemini Vision 응답 지연"})
    except VisionBusyError:
        raise HTTPException(status_code=504, detail={"code": "OCR_004", "message": "혼잡, 잠시 후 재시도해 주세요"})
    except VisionInvocationError:
        raise HTTPException(status_code=504, detail={"code": "OCR_002", "message": "Gemini Vision 호출 실패"})
    except OcrParseError:
        raise HTTPException(status_code=500, detail={"code": "OCR_003", "message": "OCR 응답 파싱 실패"})
