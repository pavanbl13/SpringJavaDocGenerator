package org.example.controller;

import org.example.service.UMLGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/api/uml")
public class UMLController {


    private static final Logger logger = LoggerFactory.getLogger(UMLController.class);

    @Autowired
    private UMLGeneratorService umlGeneratorService;

    @PostMapping("/generate")
    public ResponseEntity<?> generateUML(@RequestParam("directoryPath") String directoryPath) {
        logger.info("Received request with directoryPath: {}", directoryPath);
        try {
            File diagramFile = umlGeneratorService.generateUMLDiagram(directoryPath);
            if (!diagramFile.exists() || !diagramFile.canRead()) {
                logger.error("Generated diagram file is not accessible: {}", diagramFile.getAbsolutePath());
                return ResponseEntity.status(500).body("Generated diagram file is not accessible");
            }

            logger.info("Returning diagram file: {}", diagramFile.getAbsolutePath());
            FileSystemResource resource = new FileSystemResource(diagramFile);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + diagramFile.getName() + "\"")
                    .body(resource);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid directory path: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid directory path: " + e.getMessage());
        } catch (IOException e) {
            logger.error("Error generating UML diagram: {}", e.getMessage());
            return ResponseEntity.status(500).body("Error generating UML diagram: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage());
            return ResponseEntity.status(500).body("Unexpected error: " + e.getMessage());
        }
    }
}
