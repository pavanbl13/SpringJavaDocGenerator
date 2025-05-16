package org.example.controller;

import org.example.service.JavadocService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/javadoc")
public class JavaDocController {

    @Autowired
    private JavadocService javadocService;

    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String generateJavaDoc(@RequestParam("folderPath") String folderPath) {
        try {
            javadocService.generateJavaDoc(folderPath);
            return "JavaDoc generation completed successfully.";
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            return "An unexpected error occurred: " + e.getMessage();
        }
    }
}
