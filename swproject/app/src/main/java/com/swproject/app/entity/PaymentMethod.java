package com.swproject.app.entity;

// 결제수단 구분 (은행 CSV에는 카드 종류가 없어서 직접 지정해야 함)
public enum PaymentMethod {
    UNSPECIFIED("미지정"),     // 연말정산 계산에서 제외됨
    CREDIT("신용카드"),
    DEBIT("체크카드"),
    CASH_RECEIPT("현금영수증");

    private final String label;
    PaymentMethod(String label) { this.label = label; }
    public String getLabel() { return label; }
}