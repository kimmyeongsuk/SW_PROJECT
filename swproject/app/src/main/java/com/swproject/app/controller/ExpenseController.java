package com.swproject.app.controller;

import com.swproject.app.entity.Expense;
import com.swproject.app.entity.ExpenseType;
import com.swproject.app.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@Controller                 // Spring이 이 클래스를 관리 대상으로 등록, 연결해줌
@RequestMapping("/expenses")
@RequiredArgsConstructor   //Lombok 어노테이션, 생성자를 자동으로 만들어줌
public class ExpenseController {

    private final ExpenseService expenseService;

    // 목록 화면
    @GetMapping     // /expense 요청 처리
    public String list(Model model) {

        model.addAttribute("expenses", expenseService.findAll());
        return "expense/list";
        //Service에서 전체 지출 목록을 가져와 "expense"이름으로 Model에 담음
        //Model : Controller에서 화면으로 데이터를 전달하는 역할
        //Spring이 자동으로 expense/list.html로 렌더링함
    }

    // 등록 화면
    @GetMapping("/new")  // + 새 기록 추가 버튼 누르면 실행
    public String createForm(Model model) {

        model.addAttribute("expense", new Expense());
        model.addAttribute("types", ExpenseType.values());
        return "expense/form";
        //from.html 폼과 연결해 입력한 값이 객체에 채워짐
        //enum(INCOME,EXPENSE)의 모든 값을 배열로 전달, 옵션 목록을 만듦
    }

    // 등록 기능
    @PostMapping // 저장 버튼 누르면 여기로 데이터 전송
    public String create(@Valid @ModelAttribute Expense expense, //@Valid Expense 검증 체크
                         BindingResult bindingResult,           //@ModelAttribute from에서 입력한 값이 자동으로 Expense 객체에 채워져 들어옴
                         Model model) {
        //예외 처리
        //검증에 문제가 있을시 입력 폼으로 에러메세지와 함게 돌려보냄
        //통과 시 실제 DB에 저장
        if (bindingResult.hasErrors()) {
            model.addAttribute("types", ExpenseType.values());
            return "expense/form";
        }
        expenseService.save(expense);
        return "redirect:/expenses"; // 등록 후 목록으로 이동
    }

    //수정화면
    @GetMapping("/{id}/update") //{id}로 목록별 요청 처리
                              //@PathVariable이 이 값을 메서드 파라미터로 받아옴
    public String updateForm(@PathVariable Long id, Model model) {
        model.addAttribute("expense", expenseService.findById(id));
        model.addAttribute("types", ExpenseType.values());
        return "expense/form";
        // id의 기존 데이터를 DB 조회 후 Model에 담음
    }

    // 수정 기능
    @PostMapping("/{id}")
    public String update(@PathVariable Long id, //{id} 값을 메서드 파라미터로 가져옴
                         @Valid @ModelAttribute Expense expense, //Expense 검증 체크 후 Expense 객체 채워 들어옴
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("types", ExpenseType.values());
            return "expense/form";
        }
        //등록 기능과 다르게 기존 id 찾아 값만 update 함.
        expenseService.update(id, expense);
        return "redirect:/expenses";
    }

    // 삭제 기능
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        expenseService.delete(id);
        return "redirect:/expenses";
    }

    // 일별 지출 그래프 화면: GET /expenses/chart (예: /expenses/chart?month=2026-08)
    @GetMapping("/chart")
    public String chart(@RequestParam(required = false) String month, Model model) {
        YearMonth target = (month != null && !month.isBlank())  //month 파라미터가 비어있지 않고 값이 정상적으로 넘어옴
                ? YearMonth.parse(month)                        //
                : YearMonth.now();
        //

        model.addAttribute("chart", expenseService.getDailyChart(target));
        return "expense/chart";
    }
}
