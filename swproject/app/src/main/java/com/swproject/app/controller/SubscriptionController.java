package com.swproject.app.controller;

import com.swproject.app.entity.Subscription;
import com.swproject.app.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    // 목록 화면: GET /subscriptions
    @GetMapping
    public String list(Model model) {
        model.addAttribute("page", subscriptionService.getPage());
        return "subscription/list";
    }

    // 등록 폼: GET /subscriptions/new
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("subscription", new Subscription());
        model.addAttribute("categories", SubscriptionService.CATEGORIES);
        return "subscription/form";
    }

    // 등록 처리: POST /subscriptions
    @PostMapping
    public String create(@Valid @ModelAttribute Subscription subscription,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", SubscriptionService.CATEGORIES);
            return "subscription/form";
        }
        subscriptionService.save(subscription);
        return "redirect:/subscriptions";
    }

    // 수정 폼: GET /subscriptions/{id}/edit
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("subscription", subscriptionService.findById(id));
        model.addAttribute("categories", SubscriptionService.CATEGORIES);
        return "subscription/form";
    }

    // 수정 처리: POST /subscriptions/{id}
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute Subscription subscription,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", SubscriptionService.CATEGORIES);
            return "subscription/form";
        }
        subscriptionService.update(id, subscription);
        return "redirect:/subscriptions";
    }

    // 해지 ↔ 재개: POST /subscriptions/{id}/toggle
    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id) {
        subscriptionService.toggleActive(id);
        return "redirect:/subscriptions";
    }

    // 삭제: POST /subscriptions/{id}/delete
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        subscriptionService.delete(id);
        return "redirect:/subscriptions";
    }
}