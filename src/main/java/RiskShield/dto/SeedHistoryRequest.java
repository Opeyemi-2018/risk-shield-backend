package RiskShield.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SeedHistoryRequest(

        @Min(value = 7, message = "Days must be at least 7")
        @Max(value = 180, message = "Days must be at most 180")
        Integer days,

        Boolean replaceExisting,

        Long seed
) {
    public int daysOrDefault() {
        return days != null ? days : 60;
    }

    public boolean replaceExistingOrDefault() {
        return replaceExisting == null || replaceExisting;
    }
}