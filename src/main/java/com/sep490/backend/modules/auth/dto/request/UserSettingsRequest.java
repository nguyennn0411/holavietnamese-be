package com.sep490.backend.modules.auth.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSettingsRequest {
    /** Null means that the corresponding setting is not part of this partial update. */
    private Double audioSpeed;
    private Boolean pronunciationHintsEnabled;
    private Boolean autoTranslateEnabled;
    private Boolean notificationsEnabled;
    private Integer dailyLearningGoalMinutes;
}
