package com.swproject.app.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

public record DailyChartDto (
        String monthLabel,
        List<Integer> days,
        List<Long> amounts,
        List<Long> incomes,
        List<List<CategoryBarDto>> dayBars, // 날짜별 카테고리 막대 목록
        List<String> legendCategories,  // 범례용 카테고리
        long total,
        long totalIncome,
        long average,
        int startOffset,
        String prevMonth,
        String nextMonth,
        List<List<BillDto>> dayBills,          // 날짜별 정기구독 결제 예정 목록
        ForecastDto forecast                   // 이 달 다음 달의 예상 지출
){}

