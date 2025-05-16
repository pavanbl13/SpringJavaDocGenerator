package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootApplication
public class JavaDocGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavaDocGeneratorApplication.class, args);
    }

    // Utility method to collect Java files from a directory
    public static List<File> collectJavaFiles(String directoryPath) throws IOException {
        return Files.walk(Paths.get(directoryPath))
                .filter(path -> path.toString().endsWith(".java"))
                .map(Path::toFile)
                .collect(Collectors.toList());
    }

    // Method to generate JavaDoc for a list of Java files
    public static void generateJavaDoc(List<File> javaFiles, String outputDir, String classpath) throws IOException {
        if (javaFiles.isEmpty()) {
            throw new IOException("No Java files provided for JavaDoc generation");
        }

        // Validate and create output directory
        File outputDirFile = new File(outputDir);
        if (!outputDirFile.exists()) {
            if (!outputDirFile.mkdirs()) {
                throw new IOException("Failed to create output directory: " + outputDir);
            }
        }
        if (!outputDirFile.canWrite()) {
            throw new IOException("Output directory is not writable: " + outputDir);
        }

        // Log input files and classpath
        System.out.println("Generating JavaDoc for files: ");
        javaFiles.forEach(file -> System.out.println("  - " + file.getAbsolutePath()));
        System.out.println("Output directory: " + outputDir);
        System.out.println("Classpath: " + (classpath != null && !classpath.isEmpty() ? classpath : "none provided"));
        if (classpath == null || classpath.isEmpty()) {
            System.out.println("Warning: No classpath provided. Dependency resolution may fail for files requiring external libraries (e.g., Spring Security, Lombok).");
        }

        // Initialize Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("Java compiler not available. Ensure JDK is used instead of JRE.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(javaFiles);

        // Set up JavaDoc options
        List<String> options = new ArrayList<>();
        options.add("-d");
        options.add(outputDir);
        options.add("-sourcepath");
        options.add(javaFiles.get(0).getParent());
        options.add("-Xdoclint:none"); // Disable strict doclint
        options.add("-doclet");
        options.add("com.sun.tools.doclets.standard.Standard"); // Use standard JavaDoc doclet
        options.add("-protected"); // Include protected and public members

        // Enable Lombok annotation processing
        String processorPath = findLombokJar(classpath);
        if (processorPath != null) {
            options.add("-processorpath");
            options.add(processorPath);
            options.add("-processor");
            options.add("lombok.launch.AnnotationProcessorHider$AnnotationProcessor");
            System.out.println("Processor path: " + processorPath);
        } else {
            System.out.println("Warning: Lombok JAR not found in classpath. Annotation processing may fail.");
        }

        if (classpath != null && !classpath.isEmpty()) {
            options.add("-classpath");
            options.add(classpath);
        }

        // Log options for debugging
        System.out.println("JavaDoc options: " + String.join(" ", options));

        // Create a JavaDoc task
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
        boolean success = task.call();

        fileManager.close();

        // Log diagnostics
        StringBuilder diagnosticMessages = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            diagnosticMessages.append(String.format("JavaDoc Error: %s at %s:%d%n",
                    diagnostic.getMessage(null),
                    diagnostic.getSource() != null ? diagnostic.getSource().getName() : "unknown",
                    diagnostic.getLineNumber()));
        }

        if (!success) {
            throw new IOException("JavaDoc generation failed: " + diagnosticMessages.toString());
        }

        // Verify output for HTML files
        File[] outputFiles = outputDirFile.listFiles((dir, name) -> name.endsWith(".html"));
        if (outputFiles == null || outputFiles.length == 0) {
            // Check for unexpected .class files
            File[] classFiles = outputDirFile.listFiles((dir, name) -> name.endsWith(".class"));
            String classFilesMessage = (classFiles != null && classFiles.length > 0)
                    ? "Found " + classFiles.length + " .class files instead of HTML files."
                    : "No files found.";
            throw new IOException("JavaDoc generation completed but no HTML files were created in " + outputDir + ". " + classFilesMessage + " Diagnostics: " + diagnosticMessages);
        }

        System.out.println("JavaDoc generation completed. HTML files created: " + outputFiles.length);
    }

    // Helper method to find Lombok JAR in the classpath
    private static String findLombokJar(String classpath) {
        if (classpath == null || classpath.isEmpty()) {
            return null;
        }
        String[] paths = classpath.split(System.getProperty("path.separator"));
        for (String path : paths) {
            if (path.contains("lombok") && path.endsWith(".jar")) {
                return path;
            }
        }
        return null;
    }

    // Placeholder for future GitHub integration
    /*
    public static void generateJavaDocFromGitHub(String repoUrl, String branch, String outputDir, String classpath) throws IOException {
        // TODO: Implement GitHub repo cloning using JGit or similar library
        // Steps:
        // 1. Clone repo to temporary local directory
        // 2. Call collectJavaFiles on cloned directory
        // 3. Call generateJavaDoc with collected files
        // 4. Clean up temporary directory
    }
    */
}