package com.s2admin.module.system.controller;

import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.Result;
import com.s2admin.module.system.annotation.Log;
import com.s2admin.module.system.entity.SysMessage;
import com.s2admin.module.system.form.MessageForm;
import com.s2admin.module.system.form.MessageQuery;
import com.s2admin.module.system.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/system/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    @PreAuthorize("@ss.hasPermission('system:message:send')")
    @Log(module = "站内信", operation = "发送")
    public Result<Void> send(@RequestBody @Valid MessageForm form) {
        messageService.send(form);
        return Result.success();
    }

    @GetMapping("/my")
    public Result<PageResult<SysMessage>> my(MessageQuery query) {
        return Result.success(messageService.myPage(query));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.success(Map.of("count", messageService.unreadCount()));
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        messageService.markAllRead();
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        messageService.deleteMine(id);
        return Result.success();
    }
}
