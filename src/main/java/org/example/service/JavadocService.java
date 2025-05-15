package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils; // For StringUtils.hasText

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class JavadocService {

    private static final Logger logger = LoggerFactory.getLogger(JavadocService.class);

    @Value("${javadoc.output.base-dir:generated-javadoc}")
    private String outputBaseDir;

    @Value("${javadoc.command.path:javadoc}")
    private String javadocCommand;

    @Value("${maven.command.path:mvn}") // Or mvnw
    private String mavenCommand;

    public String generateDocs(String sourceDirectoryPath, String customClasspath) throws IOException, InterruptedException {
        Path sourcePath = Paths.get(sourceDirectoryPath);
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            throw new IllegalArgumentException("Source directory does not exist or is not a directory: " + sourceDirectoryPath);
        }

        String uniqueOutputDirName = "docs-" + UUID.randomUUID().toString().substring(0, 8);
        Path outputDirPath = Paths.get(outputBaseDir, uniqueOutputDirName).toAbsolutePath();
        Files.createDirectories(outputDirPath);

        logger.info("Source directory: {}", sourcePath.toAbsolutePath());
        logger.info("Output directory for Javadoc: {}", outputDirPath);

        List<String> javaFiles;
        try (Stream<Path> walk = Files.walk(sourcePath)) {
            javaFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(Path::toString)
                    .collect(Collectors.toList());
        }

        if (javaFiles.isEmpty()) {
            logger.warn("No .java files found in directory: {}", sourceDirectoryPath);
            return "No .java files found in " + sourceDirectoryPath + ". No Javadoc generated.";
        }

        logger.info("Found {} .java files to document.", javaFiles.size());

        // Attempt to build classpath if it's a Maven project
        String effectiveClasspath = determineEffectiveClasspath(sourcePath, customClasspath);

        List<String> command = new ArrayList<>();
        command.add(javadocCommand);
        command.add("-d");
        command.add(outputDirPath.toString());
        command.add("-sourcepath");
        command.add(sourcePath.toAbsolutePath().toString());

        if (StringUtils.hasText(effectiveClasspath)) {
            logger.info("Using effective classpath: {}", effectiveClasspath);
            command.add("-classpath");
            command.add(effectiveClasspath);
        } else {
            logger.warn("No classpath provided or determined. Javadoc might miss dependencies (e.g., Spring annotations).");
        }

        // Add encoding to handle different file encodings; UTF-8 is common
        command.add("-encoding");
        command.add("UTF-8");
        command.add("-docencoding");
        command.add("UTF-8");
        command.add("-charset");
        command.add("UTF-8");

        // Suppress warnings for common issues that might not be critical for basic doc generation
        // command.add("-Xdoclint:none"); // Uncomment if you see too many linting errors from javadoc

        command.addAll(javaFiles);

        logger.info("Executing Javadoc command: {}", String.join(" ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(sourcePath.toFile()); // Run javadoc from the source directory context
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        StringBuilder processOutput = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                processOutput.append(line).append(System.lineSeparator());
                logger.debug("Javadoc output: {}", line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode == 0) {
            logger.info("Javadoc generation successful. Output at: {}", outputDirPath);
            // Check if index.html was created
            if (Files.exists(outputDirPath.resolve("index.html"))) {
                return "Javadoc generated successfully at: " + outputDirPath.toString();
            } else {
                logger.warn("Javadoc process exited successfully, but index.html was not found in the output. Javadoc output:\n{}", processOutput.toString());
                return "Javadoc process completed, but main index file might be missing. Check logs and output at: " + outputDirPath.toString();
            }
        } else {
            logger.error("Javadoc generation failed with exit code: {}. Output:\n{}", exitCode, processOutput.toString());
            throw new RuntimeException("Javadoc generation failed. Check logs for details. Process output:\n" + processOutput.toString());
        }
    }

    private String determineEffectiveClasspath(Path projectRootPath, String customClasspath) {
        List<String> classpathElements = new ArrayList<>();
        if (StringUtils.hasText(customClasspath)) {
            classpathElements.addAll(Arrays.asList(customClasspath.split(File.pathSeparator)));
        }

        // Attempt to get Maven classpath if pom.xml exists
        Path pomFile = projectRootPath.resolve("pom.xml");
        if (Files.exists(pomFile)) {
            logger.info("pom.xml found in {}. Attempting to determine Maven classpath.", projectRootPath);
            try {
                String mavenCp = getMavenClasspath(projectRootPath);
                if (StringUtils.hasText(mavenCp)) {
                    classpathElements.addAll(Arrays.asList(mavenCp.split(File.pathSeparator)));
                    logger.info("Successfully determined Maven classpath.");
                }
            } catch (IOException | InterruptedException e) {
                logger.warn("Failed to determine Maven classpath for {}: {}", projectRootPath, e.getMessage());
                // Log the stack trace for more detailed debugging if needed
                logger.debug("Maven classpath determination error stack trace:", e);
            }
        } else {
            logger.info("No pom.xml found in {}. Skipping Maven classpath resolution.", projectRootPath);
        }

        // Deduplicate and join
        return classpathElements.stream().distinct().collect(Collectors.joining(File.pathSeparator));
    }

    private String getMavenClasspath(Path projectRootPath) throws IOException, InterruptedException {
        // Use mvnw if available, otherwise fall back to mvn
        String actualMavenCommand = mavenCommand; // Default configured command
        Path mvnwPath = projectRootPath.resolve("mvnw.cmd"); // Windows
        if (!Files.exists(mvnwPath)) {
            mvnwPath = projectRootPath.resolve("mvnw"); // Linux/macOS
        }

        if (Files.exists(mvnwPath) && Files.isExecutable(mvnwPath)) {
            actualMavenCommand = mvnwPath.toAbsolutePath().toString();
            logger.info("Using Maven Wrapper (mvnw) found at: {}", actualMavenCommand);
        } else {
            logger.info("Maven Wrapper (mvnw) not found or not executable in {}. Using configured maven command: {}", projectRootPath, actualMavenCommand);
        }


        // Output file for classpath, will be created in the target project's root
        Path classpathOutputFile = projectRootPath.resolve("javadoc_classpath.txt");

        List<String> mvnCommandList = new ArrayList<>();
        mvnCommandList.add(actualMavenCommand);
        mvnCommandList.add("dependency:build-classpath");
        mvnCommandList.add("-Dmdep.outputFile=" + classpathOutputFile.toString());
        // Define scope. 'compile' should generally be sufficient for javadoc source analysis.
        // 'runtime' or 'test' might be needed if your javadoc comments reference classes from those scopes.
        mvnCommandList.add("-DincludeScope=compile");
        // mvnCommandList.add("-Dmdep.pathSeparator=" + File.pathSeparator); // Ensure correct path separator, though default usually works

        logger.info("Executing Maven command for classpath: {}", String.join(" ", mvnCommandList));

        ProcessBuilder processBuilder = new ProcessBuilder(mvnCommandList);
        processBuilder.directory(projectRootPath.toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        StringBuilder mvnOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                mvnOutput.append(line).append(System.lineSeparator());
            }
        }
        logger.debug("Maven dependency:build-classpath output:\n{}", mvnOutput.toString());

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            logger.error("Maven dependency:build-classpath failed with exit code {}. Output:\n{}", exitCode, mvnOutput.toString());
            Files.deleteIfExists(classpathOutputFile); // Clean up
            return null;
        }

        if (!Files.exists(classpathOutputFile)) {
            logger.error("Maven command successful, but classpath output file {} was not created.", classpathOutputFile);
            return null;
        }

        String classpath = Files.readString(classpathOutputFile, StandardCharsets.UTF_8).trim();
        Files.deleteIfExists(classpathOutputFile); // Clean up

        return classpath;
    }
}
