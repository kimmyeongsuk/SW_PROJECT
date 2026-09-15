package com.swproject.app.dto;

//카테고리별 지출 합계
public record CategoryDto(String category, Long amount, int percent) {
}
