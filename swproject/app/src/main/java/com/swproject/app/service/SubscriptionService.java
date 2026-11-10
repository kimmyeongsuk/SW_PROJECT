package com.swproject.app.service;

import com.swproject.app.dto.SubscriptionPageDto;
import com.swproject.app.entity.Subscription;
import com.swproject.app.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.swproject.app.dto.BillDto;
import com.swproject.app.entity.Expense;
import com.swproject.app.entity.ExpenseType;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    // 등록 화면의 카테고리 선택지
    public static final List<String> CATEGORIES = List.of("구독", "통신", "주거/관리비", "보험", "교통", "기타");

    // 목록 화면에 필요한 데이터 조립
    @Transactional(readOnly = true)
    public SubscriptionPageDto getPage() {
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);

        List<SubscriptionPageDto.Item> items = new ArrayList<>();
        long monthlyTotal = 0;
        long remaining = 0;
        int activeCount = 0;

        for (Subscription s : subscriptionRepository.findAll()) {
            // 이번 달 결제일 (31일 결제인데 이번 달이 30일까지면 30일로 조정)
            LocalDate thisMonthDate = adjustedDate(thisMonth, s.getBillingDay());

            // 다음 결제일: 이번 달 결제일이 아직 안 지났으면(오늘 포함) 이번 달, 지났으면 다음 달
            LocalDate next = !thisMonthDate.isBefore(today)
                    ? thisMonthDate
                    : adjustedDate(thisMonth.plusMonths(1), s.getBillingDay());
            long daysLeft = ChronoUnit.DAYS.between(today, next);

            if (s.isActive()) {
                monthlyTotal += s.getAmount();
                activeCount++;
                if (!thisMonthDate.isBefore(today)) {
                    remaining += s.getAmount();   // 이번 달에 아직 안 나간 금액
                }
            }

            items.add(new SubscriptionPageDto.Item(
                    s.getId(), s.getName(), s.getAmount(), s.getBillingDay(), s.getCategory(),
                    s.isActive(), s.getMemo(), next, daysLeft));
        }

        // 사용 중인 항목을 먼저, 그 안에서는 결제일이 가까운 순. 해지된 항목은 맨 아래.
        items.sort(Comparator.comparing((SubscriptionPageDto.Item i) -> !i.active())
                .thenComparingLong(SubscriptionPageDto.Item::daysLeft)
                .thenComparing(SubscriptionPageDto.Item::name));

        return new SubscriptionPageDto(monthlyTotal, monthlyTotal * 12, activeCount, remaining, items);
    }

    // 결제일이 그 달에 없는 날짜(예: 2월 31일)면 그 달의 마지막 날로 맞춤
    private LocalDate adjustedDate(YearMonth ym, int billingDay) {
        return ym.atDay(Math.min(billingDay, ym.lengthOfMonth()));
    }

    @Transactional(readOnly = true)
    public Subscription findById(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 항목을 찾을 수 없습니다. id=" + id));
    }

    @Transactional
    public void save(Subscription subscription) {
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void update(Long id, Subscription data) {
        Subscription s = findById(id);
        s.setName(data.getName());
        s.setAmount(data.getAmount());
        s.setBillingDay(data.getBillingDay());
        s.setCategory(data.getCategory());
        s.setActive(data.isActive());
        s.setMemo(data.getMemo());
    }

    // 해지 <--> 재개
    @Transactional
    public void toggleActive(Long id) {
        Subscription s = findById(id);
        s.setActive(!s.isActive());
    }

    @Transactional
    public void delete(Long id) {
        subscriptionRepository.deleteById(id);
    }

    // ---------------------------------------------------------------
    // 내역(Expense) --> 정기구독 자동 연결
    // ---------------------------------------------------------------

    // 내역의 카테고리가 이 값이면 정기구독으로 자동 등록
    public static final String AUTO_CATEGORY = "구독";

    // 사용 중인 정기구독 월 합계 (다음 달 예측의 "고정 지출"로 사용)
    @Transactional(readOnly = true)
    public long getActiveMonthlyTotal() {
        long total = 0;
        for (Subscription s : subscriptionRepository.findAll()) {
            if (s.isActive()) total += s.getAmount();
        }
        return total;
    }

    // 사용 중인 구독 이름 목록 (예측할 때 변동 지출에서 중복 제외하는 용도)
    @Transactional(readOnly = true)
    public List<String> getActiveNames() {
        List<String> names = new ArrayList<>();
        for (Subscription s : subscriptionRepository.findAll()) {
            if (s.isActive()) names.add(s.getName());
        }
        return names;
    }

    // 지출 내역 중 카테고리가 "구독"인 것을 정기구독에 자동 등록
    //  - 같은 이름의 구독이 이미 있으면(해지된 것 포함) 새로 만들지 않음 --> 기존 데이터 보호
    //  - 같은 이름이 여러 건이면 가장 최근 내역의 금액/결제일을 사용
    // 새로 등록된 개수를 반환
    @Transactional
    public int registerFromExpenses(List<Expense> expenses) {
        if (expenses == null || expenses.isEmpty()) return 0;

        // 이름 --> 가장 최근 내역
        Map<String, Expense> latestByKey = new LinkedHashMap<>();
        for (Expense e : expenses) {
            if (e.getType() != ExpenseType.EXPENSE) continue;          // 수입은 제외
            if (!AUTO_CATEGORY.equals(e.getCategory())) continue;      // "구독"만 대상
            if (e.getAmount() == null || e.getAmount() <= 0 || e.getExpenseDate() == null) continue;

            String key = nameKey(subscriptionName(e));
            Expense prev = latestByKey.get(key);
            if (prev == null || e.getExpenseDate().isAfter(prev.getExpenseDate())) {
                latestByKey.put(key, e);
            }
        }

        // 이미 등록된 구독 이름들
        Set<String> existing = new HashSet<>();
        for (Subscription s : subscriptionRepository.findAll()) {
            existing.add(nameKey(s.getName()));
        }

        int created = 0;
        for (Map.Entry<String, Expense> entry : latestByKey.entrySet()) {
            if (existing.contains(entry.getKey())) continue;           // 이미 있으면 건너뜀

            Expense e = entry.getValue();
            Subscription s = new Subscription();
            s.setName(subscriptionName(e));
            s.setAmount(e.getAmount());
            s.setBillingDay(e.getExpenseDate().getDayOfMonth());       // 결제일 = 내역의 일(day)
            s.setCategory(AUTO_CATEGORY);
            s.setActive(true);
            s.setMemo("내역에서 자동 등록");
            subscriptionRepository.save(s);
            existing.add(entry.getKey());
            created++;
        }
        return created;
    }

    // 구독 이름 결정: 메모에 "넷플릭스" 같은 구독 키워드가 있으면 그 키워드, 없으면 메모 자체
    // (월마다 메모가 조금씩 달라도 같은 구독으로 묶기 위함)
    private String subscriptionName(Expense e) {
        String memo = e.getMemo() == null ? "" : e.getMemo().trim();
        if (memo.isEmpty()) return AUTO_CATEGORY;
        for (String keyword : ExpenseService.CATEGORY_KEYWORDS.getOrDefault(AUTO_CATEGORY, List.of())) {
            if (memo.contains(keyword)) return keyword;
        }
        return memo.length() > 100 ? memo.substring(0, 100) : memo;
    }

    // 이름 비교용 키: 공백 제거 + 소문자
    private String nameKey(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "").toLowerCase();
    }

    // ---------------------------------------------------------------
    // 지출 분석 캘린더용: 해당 월의 결제 예정일
    // ---------------------------------------------------------------

    // 그 달의 일(day) --> 그날 결제 예정인 구독 목록
    // 지난 달은 비워 둠 (이미 결제된 기록은 내역에 있으므로)
    @Transactional(readOnly = true)
    public Map<Integer, List<BillDto>> getBillsByDay(YearMonth month) {
        Map<Integer, List<BillDto>> result = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        if (month.isBefore(YearMonth.from(today))) return result;

        for (Subscription s : subscriptionRepository.findAll()) {
            if (!s.isActive()) continue;                                // 해지된 건 제외
            LocalDate date = adjustedDate(month, s.getBillingDay());    // 31일 → 말일 보정(기존 메서드 재사용)
            result.computeIfAbsent(date.getDayOfMonth(), k -> new ArrayList<>())
                    .add(new BillDto(s.getName(), s.getAmount(), date.isBefore(today)));
        }
        return result;
    }
}
