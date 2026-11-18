package com.swproject.app.service;

import com.swproject.app.dto.CatagoryForcasDto;
import com.swproject.app.entity.ExpenseType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 다음 달 "변동 지출" 예측 계산기
 *  - 직전 최대 3개월을 가까운 달부터 가중치 3 : 2 : 1 로 평균 (가중 이동평균)
 *  - 카테고리별로 같은 방식으로 계산하고, 변동 지출 합계 = 카테고리 예측의 합 (표와 합계가 항상 일치)
 *  - 고정성 지출(구독·통신 카테고리, 메모에 구독 이름이 있는 내역)과 일회성 지출은 제외
 *  - 범위 = 최근 달들의 변동 지출 중 최소 ~ 최대
 */
public final class ForecastCalculator {

    private ForecastCalculator() {}

    /** 계산에 필요한 최소 정보만 담은 거래 (Expense 엔티티를 여기에 옮겨 담아서 넘김) */
    public record Tx(LocalDate date, ExpenseType type, long amount, String category, String memo, boolean oneOff) {}

    public record Result(long variable, long low, long high,
                         List<CatagoryForcasDto> categories, int oneOffCount, long oneOffAmount) {}

    private static final int[] WMA_WEIGHTS = {3, 2, 1};        // 가장 가까운 달 --> 오래된 달
    private static final int MIN_DAYS_FOR_CURRENT_MONTH = 7;   // 이번 달은 7일 이상 지나야 사용
    private static final long RISING_MIN_DIFF = 10_000;        // "늘 것 같음" 최소 증가액(원)
    private static final int RISING_MIN_PERCENT = 10;          // "늘 것 같음" 최소 증가율(%)

    public static Result calculate(YearMonth target, LocalDate today, List<Tx> txs,
                                   Collection<String> fixedCategories, Collection<String> subscriptionNames) {
        YearMonth nowMonth = YearMonth.from(today);

        // 사용할 달들: 카테고리 --> 금액 (가까운 달이 앞)
        List<Map<String, Double>> months = new ArrayList<>();
        List<Boolean> partialFlags = new ArrayList<>();   // months 와 같은 순서: 아직 끝나지 않은 달인지
        int oneOffCount = 0;
        long oneOffAmount = 0;

        for (int i = 1; i <= WMA_WEIGHTS.length; i++) {
            YearMonth m = target.minusMonths(i);
            if (m.isAfter(nowMonth)) continue;                       // 아직 시작 안 한 달
            boolean partial = m.equals(nowMonth);                    // 아직 끝나지 않은 이번 달
            if (partial && today.getDayOfMonth() < MIN_DAYS_FOR_CURRENT_MONTH) continue;

            long monthTotal = 0;                                     // 기록 유무 판단용 (일회성·고정 포함 전체 지출)
            Map<String, Double> byCategory = new LinkedHashMap<>();
            int monthOneOffCount = 0;
            long monthOneOffAmount = 0;

            for (Tx t : txs) {
                if (t.type() != ExpenseType.EXPENSE) continue;
                if (!YearMonth.from(t.date()).equals(m)) continue;
                if (partial && t.date().isAfter(today)) continue;

                monthTotal += t.amount();
                if (isFixed(t, fixedCategories, subscriptionNames)) continue;  // 고정성 지출은 정기구독 합계로 따로 계산
                if (t.oneOff()) {                                              // 일회성 지출은 예측에서 제외
                    monthOneOffCount++;
                    monthOneOffAmount += t.amount();
                    continue;
                }
                byCategory.merge(categoryName(t.category()), (double) t.amount(), Double::sum);
            }
            if (monthTotal == 0) continue;                           // 기록 없는 달은 평균에서 제외

            if (partial) {                                           // 이번 달은 일평균으로 말일까지 환산
                double scale = (double) m.lengthOfMonth() / today.getDayOfMonth();
                byCategory.replaceAll((k, v) -> v * scale);
            }
            months.add(byCategory);
            partialFlags.add(partial);
            oneOffCount += monthOneOffCount;
            oneOffAmount += monthOneOffAmount;
        }

        if (months.isEmpty()) return new Result(0, 0, 0, List.of(), 0, 0);

        // 가중치 합 (사용 가능한 달 수만큼만)
        double weightSum = 0;
        for (int k = 0; k < months.size(); k++) weightSum += WMA_WEIGHTS[k];

        // 카테고리별 가중 이동평균. 어떤 달에 없던 카테고리는 그 달 0원으로 계산
        Set<String> cats = new LinkedHashSet<>();
        for (Map<String, Double> mo : months) cats.addAll(mo.keySet());

        Map<String, Double> forecast = new HashMap<>();
        double variable = 0;
        for (String c : cats) {
            double sum = 0;
            for (int k = 0; k < months.size(); k++) sum += WMA_WEIGHTS[k] * months.get(k).getOrDefault(c, 0.0);
            double f = sum / weightSum;
            forecast.put(c, f);
            variable += f;
        }

        // 범위: 달별 변동 지출 합계의 최소/최대. 예측값이 반드시 범위 안에 들어가도록 천원 단위로 내림/올림
        double min = Double.MAX_VALUE, max = 0;
        for (Map<String, Double> mo : months) {
            double s = mo.values().stream().mapToDouble(Double::doubleValue).sum();
            min = Math.min(min, s);
            max = Math.max(max, s);
        }
        long low = Math.min(Math.round(variable), floorThousand(min));
        long high = Math.max(Math.round(variable), ceilThousand(max));

        // 카테고리별 결과: "직전에 끝난 달" 대비 증감.
        // (진행 중인 이번 달은 일평균 환산값이라 들쭉날쭉해서 비교 기준으로 쓰지 않음. 끝난 달이 없을 때만 사용)
        int baseIdx = 0;
        for (int k = 0; k < months.size(); k++) {
            if (!partialFlags.get(k)) { baseIdx = k; break; }
        }
        Map<String, Double> latest = months.get(baseIdx);

        List<CatagoryForcasDto> list = new ArrayList<>();
        for (String c : cats) {
            long f = Math.round(forecast.get(c));
            long last = Math.round(latest.getOrDefault(c, 0.0));
            long diff = f - last;
            int pct = last == 0 ? 0 : (int) Math.round(diff * 100.0 / last);
            boolean rising = diff >= RISING_MIN_DIFF && (last == 0 || pct >= RISING_MIN_PERCENT);
            list.add(new CatagoryForcasDto(c, f, last, diff, pct, rising));
        }
        list.sort(Comparator.comparingLong(CatagoryForcasDto::forecast).reversed());   // 예상 금액 큰 순

        return new Result(Math.round(variable), low, high, list, oneOffCount, oneOffAmount);
    }

    // 카테고리가 비어 있으면 "미분류"로 합침
    private static String categoryName(String c) {
        return (c == null || c.isBlank()) ? "미분류" : c;
    }

    // 고정성 지출인가? (카테고리가 구독/통신이거나, 메모에 사용 중인 구독 이름이 들어 있음)
    private static boolean isFixed(Tx t, Collection<String> fixedCategories, Collection<String> subNames) {
        if (fixedCategories.contains(t.category())) return true;
        String memo = t.memo();
        if (memo == null || memo.isBlank()) return false;
        for (String name : subNames) {
            if (name != null && !name.isBlank() && memo.contains(name)) return true;
        }
        return false;
    }

    private static long floorThousand(double v) { return (long) (Math.floor(v / 1000.0) * 1000); }
    private static long ceilThousand(double v) { return (long) (Math.ceil(v / 1000.0) * 1000); }
}