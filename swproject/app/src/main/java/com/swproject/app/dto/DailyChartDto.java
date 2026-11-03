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
        List<String> lengendCategories,  // 범례용 카테고리
        long total,
        long totalIncome,
        long average,
        int startOffset,
        String prevMonth,
        String nextMonth
){}

