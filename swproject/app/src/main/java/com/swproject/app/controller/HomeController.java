package com.swproject.app.controller;

import com.swproject.app.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 앱의 첫 화면(대시보드)을 담당하는 컨트롤러.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ExpenseService expenseService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("summary", expenseService.getHomeDto());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN);
        model.addAttribute("todayLabel", java.time.LocalDate.now().format(formatter));

        return "home";
    }
}
