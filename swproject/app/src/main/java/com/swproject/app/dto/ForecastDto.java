package com.swproject.app.dto;

import java.util.List;

/** 다음 달 예상 지출 (고정/변동 분리 + 가중 이동평균) */
public record ForecastDto(
        long fixed,                           // 고정 지출 = 사용 중인 정기구독 합계
        long variable,                        // 변동 지출 = 가중 이동평균
        long total,                           // 예상 총 지출 = 고정 + 변동
        long totalLow,                        // 예상 범위 아래쪽 (고정 + 최근 달 중 가장 적었던 변동지출)
        long totalHigh,                       // 예상 범위 위쪽 (고정 + 최근 달 중 가장 많았던 변동지출)
        List<CatagoryForcasDto> categories, // 변동 지출의 카테고리별 예상 (예상 금액 큰 순)
        int oneOffCount,                      // 예측에서 제외한 일회성 지출 건수
        long oneOffAmount                     // 예측에서 제외한 일회성 지출 금액
) {
    /** 범위를 보여줄 만한가? (최근 달이 1개뿐이면 low == high 라서 범위가 없음) */
    public boolean hasRange() {
        return totalHigh - totalLow >= 1000;
    }
}