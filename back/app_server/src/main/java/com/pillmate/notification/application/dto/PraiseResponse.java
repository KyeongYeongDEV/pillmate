package com.pillmate.notification.application.dto;

// alreadyPraised=false: 이번 요청으로 칭찬 적재+FCM 발송됨 / true: 이미 이 활동을 칭찬한 적 있어 스킵됨(멱등)
public record PraiseResponse(boolean alreadyPraised) {
}
