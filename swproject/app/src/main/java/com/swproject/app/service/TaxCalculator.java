package com.swproject.app.service;

public final class TaxCalculator {
    private TaxCalculator() {}

    public record Result(long threshold,           // 최소 사용 금액(총급여×비율)
                         boolean thresholdReached, // 문턱 넘었는지
                         long remainToThreshold,   // 문턱까지 남은 금액
                         long rawDeduction,        // 한도 적용 전 공제액
                         long limit,               // 공제 한도
                         long deduction,           // 최종 공제 금액(소득공제)
                         boolean limitReached) {}  // 한도 도달 여부

    public static Result calculate(long salary, int children, long credit, long debitCash) {
        long threshold = (long) (salary * TaxRules.THRESHOLD_RATE);

        // 문턱은 신용카드 사용액부터 먼저 채운다고 가정 (⚠️ 순서 확인 필요)
        long creditIn = Math.min(credit, threshold);
        long debitIn = Math.min(debitCash, threshold - creditIn);

        // 문턱을 넘은 초과분에만 공제율 적용
        long raw = (long) ((credit - creditIn) * TaxRules.CREDIT_RATE
                + (debitCash - debitIn) * TaxRules.DEBIT_CASH_RATE);

        long limit = TaxRules.baseLimit(salary, children);
        long total = credit + debitCash;
        return new Result(threshold, total >= threshold, Math.max(0, threshold - total),
                raw, limit, Math.min(raw, limit), raw >= limit);
    }
}