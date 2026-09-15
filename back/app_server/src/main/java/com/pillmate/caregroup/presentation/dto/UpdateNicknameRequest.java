package com.pillmate.caregroup.presentation.dto;

import jakarta.validation.constraints.Size;

// nickname null/공백 허용 — 별명 리셋(기본값=본인 실제 이름) 신호로 쓰인다.
public record UpdateNicknameRequest(@Size(max = 20) String nickname) {}
