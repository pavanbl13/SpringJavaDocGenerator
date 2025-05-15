package org.example.controller;

import org.example.JavaDocGeneratorApplication;
import org.example.model.JavaDocRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/javadoc")
public class JavaDocController {

    @PostMapping("/generate")
    public ResponseEntity<String> generateJavaDoc(@RequestBody JavaDocRequest request) {
        try {
            // Validate input
            File directory = new File(request.getDirectoryPath());
            if (!directory.exists() || !directory.isDirectory()) {
                return ResponseEntity.badRequest()
                        .body("Invalid directory path: " + request.getDirectoryPath());
            }

            // Ensure output directory exists
            File outputDir = new File(request.getOutputDir());
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            // Collect Java files and generate JavaDoc
            List<File> javaFiles = JavaDocGeneratorApplication.collectJavaFiles(request.getDirectoryPath());
            if (javaFiles.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No Java files found in the specified directory");
            }

            JavaDocGeneratorApplication.generateJavaDoc(javaFiles, request.getOutputDir(), request.getClasspath());
            return ResponseEntity.ok("JavaDoc generated successfully at: " + request.getOutputDir());

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating JavaDoc: " + e.getMessage());
        }
    }

    // Placeholder for future GitHub endpoint
    /*
    @PostMapping("/generate-from-github")
    public ResponseEntity<String> generateJavaDocFromGitHub(@RequestBody GitHubJavaDocRequest request) {
        try {
            JavaDocGeneratorApplication.generateJavaDocFromGitHub(
                request.getRepoUrl(),
                request.getBranch(),
                request.getOutputDir(),
                request.getClasspath()
            );
            return ResponseEntity.ok("JavaDoc generated from GitHub repo at: " + request.getOutputDir());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating JavaDoc from GitHub: " + e.getMessage());
        }
    }
    */
}