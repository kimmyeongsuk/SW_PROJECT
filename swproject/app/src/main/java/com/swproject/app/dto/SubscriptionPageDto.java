package com.swproject.app.dto;

import java.time.LocalDate;
import java.util.List;

/** 정기구독 목록 화면 하나를 그리는 데 필요한 데이터 */
public record SubscriptionPageDto(
        long monthlyTotal,          // 월 고정지출 (사용 중인 항목만)
        long yearlyTotal,           // 연 환산 (월 × 12)
        int activeCount,            // 사용 중인 항목 수
        long remainingThisMonth,    // 이번 달 남은 결제 예정액 (오늘 포함, 이후)
        List<Item> items            // 화면에 보여줄 순서대로 정렬된 목록
) {
    /** 목록의 한 줄 */
    public record Item(
            Long id,
            String name,
            long amount,
            int billingDay,
            String category,
            boolean active,
            String memo,
            LocalDate nextBillingDate,   // 다음 결제일
            long daysLeft                // 오늘부터 다음 결제일까지 남은 일수
    ) {
    }
}