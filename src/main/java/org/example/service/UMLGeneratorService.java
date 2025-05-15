package org.example.service;

import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Service
public class UMLGeneratorService {

    public File generateUMLDiagram(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory path");
        }

        StringBuilder plantUML = new StringBuilder();
        plantUML.append("@startuml\n");

        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> processJavaFile(path, plantUML));
        }

        plantUML.append("@enduml");

        // Generate PNG file from PlantUML string
        File outputFile = new File("uml_diagram.png");
        SourceStringReader reader = new SourceStringReader(plantUML.toString());
        try (FileOutputStream output = new FileOutputStream(outputFile)) {
            reader.outputImage(output, new FileFormatOption(FileFormat.PNG));
        }

        return outputFile;
    }

    private void processJavaFile(Path path, StringBuilder plantUML) {
        try {
            String content = Files.readString(path);
            String className = extractClassName(content);
            String packageName = extractPackageName(content);

            if (className != null) {
                plantUML.append("class ").append(packageName != null ? packageName + "." : "")
                        .append(className).append(" {\n");

                extractMethodsAndFields(content, plantUML);

                plantUML.append("}\n");
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + path + ": " + e.getMessage());
        }
    }

    private String extractClassName(String content) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.contains("class ") && !line.contains("interface ")) {
                // Find the index of "class "
                int classIndex = line.indexOf("class ") + 6;
                // Extract substring after "class "
                String afterClass = line.substring(classIndex).trim();
                // Split by whitespace or curly brace to get class name
                String[] parts = afterClass.split("[\\s\\{]");
                if (parts.length > 0) {
                    return parts[0].trim();
                }
            }
        }
        return null;
    }

    private String extractPackageName(String content) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.startsWith("package ")) {
                return line.replace("package ", "").replace(";", "").trim();
            }
        }
        return null;
    }

    private void extractMethodsAndFields(String content, StringBuilder plantUML) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.contains("(") && line.contains(")") && !line.contains("class ")) {
                String method = line.split("\\{")[0].trim();
                plantUML.append("  +").append(method).append("\n");
            } else if (line.contains(";") && !line.contains("import ") && !line.contains("package ")) {
                String field = line.split(";")[0].trim();
                if (!field.isEmpty() && !field.contains("class ")) {
                    plantUML.append("  -").append(field).append("\n");
                }
            }
        }
    }
}