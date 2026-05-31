package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.GroupDetailResponse;

public interface GetGroupDetailUseCase {

    GroupDetailResponse detail(Long groupId, Long userId);
}
