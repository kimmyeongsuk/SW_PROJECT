package com.swproject.app.dto;

import java.util.List;

public record DailyChartDto (
        String monthLabel,
        List<Integer> days,
        List<Long> amounts,
        long total,
        long average,
        String prevMonth,
        String nextMonth
){}

