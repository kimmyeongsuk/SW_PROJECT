package com.swproject.app.service;

import com.swproject.app.dto.SubscriptionPageDto;
import com.swproject.app.entity.Subscription;
import com.swproject.app.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}