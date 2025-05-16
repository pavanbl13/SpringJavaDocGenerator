package org.example.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UMLGeneratorService {

    public String generatePlantUMLSource(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        if (!directory.isDirectory()) {
            throw new IOException("Invalid directory path: " + directoryPath);
        }

        // Collect all classes/interfaces and their relationships
        Set<String> classNames = new HashSet<>();
        List<String> relationships = new ArrayList<>();
        StringBuilder plantUML = new StringBuilder("@startuml\n");

        // First pass: Collect class/interface names
        try (var paths = Files.walk(directory.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectClassNames(path, classNames));
        }

        // Second pass: Process files to generate PlantUML and relationships
        try (var paths = Files.walk(directory.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> processJavaFile(path, plantUML, classNames, relationships));
        }

        // Append relationships
        relationships.forEach(rel -> plantUML.append(rel).append("\n"));
        plantUML.append("@enduml\n");

        System.out.println("Generated PlantUML:\n" + plantUML);
        return plantUML.toString();
    }

    private void collectClassNames(Path path, Set<String> classNames) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            String packageName = cu.getPackageDeclaration()
                    .map(NodeWithName::getNameAsString)
                    .orElse("");
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                String fullName = packageName.isEmpty() ? cls.getNameAsString() : packageName + "." + cls.getNameAsString();
                classNames.add(fullName);
                System.out.println("Found class/interface: " + fullName);
            });
        } catch (IOException e) {
            System.err.println("Error parsing file " + path + ": " + e.getMessage());
        }
    }

    private void processJavaFile(Path path, StringBuilder plantUML, Set<String> classNames, List<String> relationships) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            String packageName = cu.getPackageDeclaration()
                    .map(NodeWithName::getNameAsString)
                    .orElse("");

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                String className = cls.getNameAsString();
                String fullClassName = packageName.isEmpty() ? className : packageName + "." + className;
                System.out.println("Processing class/interface: " + fullClassName);

                // Start class/interface definition
                plantUML.append(cls.isInterface() ? "interface " : "class ")
                        .append(fullClassName)
                        .append(" {\n");

                // Fields
                cls.getFields().forEach(field -> {
                    String fieldDecl = formatField(field);
                    plantUML.append("  ").append(fieldDecl).append("\n");
                    // Detect associations
                    field.getVariables().forEach(var -> {
                        if (var.getType().isClassOrInterfaceType()) {
                            String type = resolveType(var.getType().asClassOrInterfaceType(), packageName, cu, classNames);
                            if (classNames.contains(type)) {
                                relationships.add(fullClassName + " --> " + type + " : " + var.getNameAsString());
                            }
                        }
                    });
                });

                // Methods
                cls.getMethods().forEach(method -> {
                    String methodDecl = formatMethod(method);
                    plantUML.append("  ").append(methodDecl).append("\n");
                    // Detect dependencies in parameters and return type
                    method.getParameters().forEach(param -> {
                        if (param.getType().isClassOrInterfaceType()) {
                            String type = resolveType(param.getType().asClassOrInterfaceType(), packageName, cu, classNames);
                            if (classNames.contains(type)) {
                                relationships.add(fullClassName + " ..> " + type);
                            }
                        }
                    });
                    if (method.getType().isClassOrInterfaceType()) {
                        String returnType = resolveType(method.getType().asClassOrInterfaceType(), packageName, cu, classNames);
                        if (classNames.contains(returnType)) {
                            relationships.add(fullClassName + " ..> " + returnType);
                        }
                    }
                });

                plantUML.append("}\n");

                // Inheritance and implementation
                cls.getExtendedTypes().forEach(type -> {
                    String superClass = resolveType(type, packageName, cu, classNames);
                    if (classNames.contains(superClass)) {
                        relationships.add(fullClassName + " <|.. " + superClass);
                    }
                });
                cls.getImplementedTypes().forEach(type -> {
                    String interfaceName = resolveType(type, packageName, cu, classNames);
                    if (classNames.contains(interfaceName)) {
                        relationships.add(fullClassName + " <|.. " + interfaceName);
                    }
                });
            });
        } catch (IOException e) {
            System.err.println("Error processing file " + path + ": " + e.getMessage());
        }
    }

    private String formatField(FieldDeclaration field) {
        String visibility = field.isPublic() ? "+" : field.isPrivate() ? "-" : field.isProtected() ? "#" : "~";
        String type = field.getVariables().get(0).getType().asString();
        String name = field.getVariables().get(0).getNameAsString();
        return visibility + " " + name + " : " + type;
    }

    private String formatMethod(MethodDeclaration method) {
        String visibility = method.isPublic() ? "+" : method.isPrivate() ? "-" : method.isProtected() ? "#" : "~";
        String name = method.getNameAsString();
        String returnType = method.getType().asString();
        String params = method.getParameters().stream()
                .map(p -> p.getNameAsString() + ": " + p.getType().asString())
                .collect(Collectors.joining(", "));
        return visibility + " " + name + "(" + params + ") : " + returnType;
    }

    private String resolveType(ClassOrInterfaceType type, String packageName, CompilationUnit cu, Set<String> classNames) {
        String typeName = type.getNameAsString();
        // Resolve imports
        Optional<String> resolved = cu.getImports().stream()
                .filter(imp -> imp.getName().getQualifier()
                        .map(q -> q.asString().equals(packageName))
                        .orElse(false) &&
                        imp.getName().getIdentifier().equals(typeName))
                .map(NodeWithName::getNameAsString)
                .findFirst();
        if (resolved.isPresent()) {
            return resolved.get();
        }
        // Check same package
        String samePackageType = packageName.isEmpty() ? typeName : packageName + "." + typeName;
        if (classNames.contains(samePackageType)) {
            return samePackageType;
        }
        // Default to simple name if not resolved
        return typeName;
    }
}