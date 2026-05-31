package com.pillmate.activity.presentation;

import com.pillmate.activity.application.ActivityFeedQueryService;
import com.pillmate.activity.application.dto.ActivityFeedItem;
import com.pillmate.common.response.ApiResponse;
import com.pillmate.common.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/activity")
@RequiredArgsConstructor
public class ActivityFeedController {

    private static final int DEFAULT_LIMIT = 20;

    private final ActivityFeedQueryService activityFeedQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ActivityFeedItem>>> getActivity(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long groupId) {
        Long viewerId = UserContext.get();
        List<ActivityFeedItem> feeds = activityFeedQueryService.query(
                viewerId, groupId, Math.min(limit, DEFAULT_LIMIT));
        return ResponseEntity.ok(ApiResponse.success(feeds));
    }
}
