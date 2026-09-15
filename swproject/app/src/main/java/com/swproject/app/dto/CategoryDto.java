package com.swproject.app.dto;

/**
 * 홈 화면 도넛 차트에 쓰일 "카테고리별 지출 합계" 한 줄을 담는 DTO.
 * Repository의 group by 쿼리 결과(Object[])를 이 형태로 변환해서 씁니다.
 */
public record CategoryDto(String category, Long amount, int percent) {
}
