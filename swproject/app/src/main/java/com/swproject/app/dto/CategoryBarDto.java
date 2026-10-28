package com.swproject.app.dto;

import java.util.List;

// 캘린더 한 칸안의 막대 하나(카테고리 하나씩)
public record CategoryBarDto (
        String category,  //카테고리 이름
        long amount, //그날 그 카테고리 지출액
        int colorIndex, //색상 번호
        int heightPercent, // 막대 높이 (%)
        List<String> items // 말풍선에 보여줄 거래목록
) {
}
