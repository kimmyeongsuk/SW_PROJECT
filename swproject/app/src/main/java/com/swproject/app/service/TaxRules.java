package com.swproject.app.service;

// 연말정산 규칙 상수. 법이 바뀌면 여기만 고치면 됨
public final class TaxRules {
    private TaxRules() {}

    // 아래 3개는 아직 확인 못 한 값 → 홈택스/국세청에서 확인 후 수정
    public static final double THRESHOLD_RATE = 0.25;   // 총급여의 25%를 넘게 써야 공제 시작
    public static final double CREDIT_RATE = 0.15;      // 신용카드 공제율
    public static final double DEBIT_CASH_RATE = 0.30;  // 체크카드·현금영수증 공제율

    // 확인된 값: 총급여 7천만 원 기준 공제한도
    public static final long SALARY_BOUNDARY = 70_000_000L;

    // 기본 공제한도: 7천만 이하 300/350/400만 (자녀 0/1/2명+), 초과 250/275/300만
    public static long baseLimit(long salary, int children) {
        int c = Math.max(0, Math.min(children, 2));
        return salary <= SALARY_BOUNDARY
                ? 3_000_000L + 500_000L * c
                : 2_500_000L + 250_000L * c;
    }
}