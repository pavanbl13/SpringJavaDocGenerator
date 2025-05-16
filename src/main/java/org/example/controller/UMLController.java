package org.example.controller;

import org.example.service.UMLGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

@RestController
@RequestMapping("/api/uml")
@CrossOrigin(origins = "*") // Refine for production
public class UMLController {

    @Value("${uml.allowed-base-directory}")
    private String allowedBaseDirectory;

    private static final Logger logger = LoggerFactory.getLogger(UMLController.class);

    @Autowired
    private UMLGeneratorService umlGeneratorService;

    @PostMapping("/generate")
    public ResponseEntity<?> generateUML(@RequestParam("directoryPath") String directoryPath) {
        logger.info("Received request for directory: {}", directoryPath);
        try {
            // Validate directory
            Path allowedBasePath = Paths.get(allowedBaseDirectory).toAbsolutePath();
            Path userPath = Paths.get(directoryPath).toAbsolutePath();

            if (!userPath.startsWith(allowedBasePath)) {
                logger.warn("Invalid directory path: {} not within {}", userPath, allowedBasePath);
                return ResponseEntity.badRequest().body("Invalid directory path: Must be within " + allowedBasePath);
            }

            File directory = userPath.toFile();
            if (!directory.exists() || !directory.isDirectory()) {
                logger.warn("Directory does not exist or is not a directory: {}", userPath);
                return ResponseEntity.badRequest().body("Invalid directory path");
            }

            String plantUMLSource = umlGeneratorService.generatePlantUMLSource(directoryPath);
            logger.debug("Generated PlantUML:\n{}", plantUMLSource);

            // Return PlantUML as JSON
            return ResponseEntity.ok(Collections.singletonMap("plantUML", plantUMLSource));
        } catch (IOException e) {
            logger.error("Error generating UML for path {}: {}", directoryPath, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating UML: " + e.getMessage());
        }
    }
}