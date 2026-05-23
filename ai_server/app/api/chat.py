from __future__ import annotations

from fastapi import APIRouter, Depends

from app.domain.chat import ChatRequest, ChatResponse
from app.rag.chain import ChatService

router = APIRouter(prefix="/api/v1")


def get_chat_service() -> ChatService:
    raise RuntimeError("ChatService dependency must be overridden at app startup")


@router.post("/chat", response_model=ChatResponse, response_model_by_alias=True)
async def chat(
    request: ChatRequest,
    service: ChatService = Depends(get_chat_service),
) -> ChatResponse:
    return await service.answer(request.question)
