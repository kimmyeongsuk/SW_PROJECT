package com.swproject.app.dto;

/** 다음 달 예상 지출 (고정/변동 분리 + 가중 이동평균) */
public record ForecastDto(
        long fixed,      // 고정 지출 = 사용 중인 정기구독 합계
        long variable,   // 변동 지출 = 최근 월들의 가중 이동평균
        long total       // 예상 총 지출 = 고정 + 변동
) {
}