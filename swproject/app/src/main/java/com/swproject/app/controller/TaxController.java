package com.swproject.app.controller;

import com.swproject.app.service.TaxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequestMapping("/tax")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;

    // 연말정산 미리보기 화면: GET /tax?year=2026
    @GetMapping
    public String summary(@RequestParam(required = false) Integer year, Model model) {
        int y = (year != null) ? year : LocalDate.now().getYear();
        model.addAttribute("s", taxService.getSummary(y));
        return "tax/summary";
    }

    // 총급여/자녀 수 저장: POST /tax/profile
    @PostMapping("/profile")
    public String saveProfile(@RequestParam int year, @RequestParam long salary,
                              @RequestParam(defaultValue = "0") int children) {
        taxService.saveProfile(year, salary, children);
        return "redirect:/tax?year=" + year;
    }
}