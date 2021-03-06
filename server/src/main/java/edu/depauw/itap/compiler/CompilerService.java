package edu.depauw.itap.compiler;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.springframework.stereotype.Service;

@Service
public class CompilerService {

  public static Path getDirectoryPath(String session) {
    return Paths.get(".").resolve("temp").resolve(session).normalize().toAbsolutePath();
  }

  /**
   * Returns the FQCN of a given source string.
   * 
   * @param source the source to use
   * @return the FQCN of the source
   * @throws RuntimeException if a FQCN could not be created
   */
  public static String getFullyQualifiedClassName(String source) throws RuntimeException {
    List<String> className = new ArrayList<>();

    Pattern r =
        Pattern.compile("^\\s*package\\s+((?:[_a-zA-Z][a-zA-Z0-9]+\\.?)*)\\s*;", Pattern.MULTILINE);
    Matcher m = r.matcher(source);

    if (m.find()) {
      className.add(m.group(1));
    }

    r = Pattern.compile("\\s*(?:public\\s+)?class\\s+([_a-zA-Z][a-zA-Z0-9]+)\\s*\\{",
        Pattern.MULTILINE);
    m = r.matcher(source);

    if (m.find()) {
      className.add(m.group(1));
    } else {
      throw new RuntimeException("Source Class name not found.");
    }

    return Joiner.on(".").join(className);
  }

  /**
   * Recursively deletes a directory.
   * 
   * @param directoryToBeDeleted the directory to delete
   * @return whether the directory was sucessfully deleted
   */
  public static boolean deleteDirectory(File directoryToBeDeleted) {
    File[] allContents = directoryToBeDeleted.listFiles();
    if (allContents != null) {
      for (File file : allContents) {
        deleteDirectory(file);
      }
    }
    return directoryToBeDeleted.delete();
  }

  /**
   * Compiles a list of sources.
   * 
   * @param session the session for which the sources correspond to
   * @param sources the sources to compile
   * @return the errors from the compiler
   */
  public List<CompilerResult> compile(String session, List<String> sources) {

    Path root = getDirectoryPath(session);
    root.toFile().mkdirs();

    Map<String, String> classNameToSource;

    try {
      classNameToSource = sources.stream().collect(
          Collectors.toMap(source -> getFullyQualifiedClassName(source), Function.identity()));
    } catch (RuntimeException e) {
      throw new RuntimeException(e);
    }

    List<JavaFileObject> compilationUnits = classNameToSource.entrySet().stream().map((entry) -> {
      return new StringJavaFileObject(entry.getKey(), entry.getValue());
    }).collect(Collectors.toList());

    Writer errorOut = new StringWriter();

    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

    List<String> compilerOptions = new ArrayList<>();

    compilerOptions.add("-d");
    compilerOptions.add(root.toString());
    compilerOptions.add("-g");

    // Compile source file.
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    JavaCompiler.CompilationTask task =
        compiler.getTask(errorOut, null, diagnostics, compilerOptions, null, compilationUnits);

    boolean success = task.call();

    List<CompilerResult> errors = new ArrayList<>();

    if (!success) {
      Map<String, List<String>> classNameToSourceLines = new HashMap<>();
      Splitter lineSplitter = Splitter.onPattern("\\R");
      for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
        String className = ((StringJavaFileObject) diagnostic.getSource()).getName();
        List<String> lines = classNameToSourceLines.computeIfAbsent(className, (k) -> {
          return lineSplitter.splitToList(classNameToSource.get(className));
        });

        FilePosition startPosition = calculatePosition(lines, diagnostic.getStartPosition());
        FilePosition endPosition = calculatePosition(lines, diagnostic.getEndPosition());

        CompilerResult result = new CompilerResult().setClassName(className)
            .setMessage(diagnostic.getMessage(null)).setSeverity(diagnostic.getKind().name())
            .setStartLineNumber(startPosition.getLineNumber())
            .setStartColumnNumber(startPosition.getColumnNumber())
            .setEndLineNumber(endPosition.getLineNumber())
            .setEndColumnNumber(endPosition.getColumnNumber());

        errors.add(result);
      }
    }

    return errors;
  }

  /**
   * Compiles a list of sources without saving their results.
   * 
   * @param session the session for which the sources correspond to
   * @param sources the sources to compile
   * @return the errors from the compiler
   */
  public List<CompilerResult> compileWithoutSaving(String session, List<String> sources) {
    List<CompilerResult> results = compile(session, sources);

    Path root = getDirectoryPath(session);

    deleteDirectory(root.toFile());

    return results;
  }

  private static FilePosition calculatePosition(List<String> lines, long position) {
    String line;
    for (int i = 0; i < lines.size(); i++) {
      line = lines.get(i);
      if (line.length() > position) {
        return new FilePosition(i + 1, position + 1);
      } else {
        position -= line.length() + 1; // Plus one due to new line characters
      }
    }
    return new FilePosition(-1L, -1L);
  }

  private static class FilePosition {
    private long lineNumber;
    private long columnNumber;

    public FilePosition(long lineNumber, long columnNumber) {
      this.lineNumber = lineNumber;
      this.columnNumber = columnNumber;
    }

    public long getLineNumber() {
      return this.lineNumber;
    }

    public long getColumnNumber() {
      return this.columnNumber;
    }
  }

  private static class StringJavaFileObject extends SimpleJavaFileObject {
    private final String code;
    private final String name;

    public StringJavaFileObject(String name, String code) {
      super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
      this.code = code;
      this.name = name;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
      return this.code;
    }

    public String getName() {
      return this.name;
    }
  }
}
