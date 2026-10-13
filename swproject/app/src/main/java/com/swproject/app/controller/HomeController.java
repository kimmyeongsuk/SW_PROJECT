package com.swproject.app.controller;

import com.swproject.app.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Controller
@RequiredArgsConstructor // 생성자 자동 생성
public class HomeController {

    //지출 관련 데이터 조회 서비스 담당 객체
    private final ExpenseService expenseService;

    @GetMapping("/")
    public String home(Model model) {
        //홈 화면에 필요한 데이터 가져와 home.html의 summury 데이터에 접근
        model.addAttribute("summary", expenseService.getHomeDto());

        //현재 날짜 가져와 todayLabel으로 표기 사용
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN);
        model.addAttribute("todayLabel", java.time.LocalDate.now().format(formatter));

        return "home";
    }
}
