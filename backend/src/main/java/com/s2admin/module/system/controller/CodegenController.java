package com.s2admin.module.system.controller;

import com.s2admin.module.system.form.CodegenForm;
import com.s2admin.module.system.service.CodegenService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/tools/codegen")
@RequiredArgsConstructor
public class CodegenController {

    private final CodegenService codegenService;

    @PostMapping
    @PreAuthorize("@ss.hasPermission('tools:codegen:generate')")
    public void generate(@RequestBody @Valid CodegenForm form, HttpServletResponse response) throws IOException {
        byte[] zip = codegenService.generate(form);
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=codegen.zip");
        response.getOutputStream().write(zip);
    }
}
