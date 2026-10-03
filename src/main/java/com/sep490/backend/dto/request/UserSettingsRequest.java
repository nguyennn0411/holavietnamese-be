package com.sep490.backend.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSettingsRequest {
    private Double audioSpeed = 1.0;
    private Boolean pronunciationHintsEnabled = true;
    private Boolean autoTranslateEnabled = true;
    private Boolean notificationsEnabled = true;
    private Integer dailyLearningGoalMinutes = 15;
}
