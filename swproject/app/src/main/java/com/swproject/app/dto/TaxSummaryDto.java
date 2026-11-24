package com.swproject.app.dto;

import com.swproject.app.service.TaxCalculator;

// 화면에 보여줄 연말정산 요약 (Thymeleaf에서 s.field() 로 접근)
public record TaxSummaryDto(int year,
                            long totalSalary, int children,
                            long credit, long debit, long cash,   // 결제수단별 올해 누적
                            long unspecified,                     // 미지정 지출 (계산 제외)
                            long excluded,                        // 제외 항목 합계 (통신·보험 등)
                            TaxCalculator.Result now,             // 현재까지 기준
                            TaxCalculator.Result projected,       // 연말 예상 기준
                            boolean hasProfile) {}