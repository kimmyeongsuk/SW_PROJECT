package com.swproject.app.dto;

// 캘린더 날짜 칸에 표시할 정기구독 결제 예정 1건
public record BillDto(
        String name,   // 구독 이름
        long amount,   // 결제 금액
        boolean past   // 이번 달에서 이미 지난 결제일인지 (회색 표시용)
) {
}