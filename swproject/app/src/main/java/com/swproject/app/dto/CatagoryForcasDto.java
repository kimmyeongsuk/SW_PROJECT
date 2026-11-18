package com.swproject.app.dto;

public record CatagoryForcasDto(
        String category,    // 카테고리 이름
        long forecast,      // 다음 달 예상 금액 (가중 이동평균)
        long lastMonth,     // 직전에 끝난 달의 실제 금액 (비교 기준)
        long diff,          // 예상 - 직전에 끝난 달 (양수면 늘 것 같음)
        int diffPercent,    // 직전에 끝난 달 대비 증감률(%). 그 달이 0원이면 0
        boolean rising      // "늘 것 같은 항목" 표시 여부
) {
}
