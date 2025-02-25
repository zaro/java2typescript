package org.bsc.processor;

import org.bsc.java2typescript.TSGlobal;
import org.bsc.java2typescript.TSNamespace;
import org.bsc.java2typescript.TSType;
import org.bsc.java2typescript.Java2TSConverter;
import org.bsc.processor.annotation.Java2TS;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.FileObject;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.bsc.java2typescript.Java2TSConverter.PREDEFINED_TYPES;

/**
 * <p>
 * Processor for annotations in {@link org.bsc.processor.annotation}.
 * </p>
 * <p>
 * Supported options:
 * </p>
 * <ul>
 *     <li>{@code ts.outfile}: target file for typescript declarations</li>
 *     <li>{@code compatibility}: specify compatibility with a given script engine
 *     (NASHORN, RHINO, V8)</li>
 * </ul>
 */
//@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes("org.bsc.processor.annotation.*")
@SupportedOptions({"ts.outfile", "ts.outdir", "compatibility"})
@org.kohsuke.MetaInfServices(javax.annotation.processing.Processor.class)
public class TypescriptProcessor extends AbstractProcessorEx {

  final static String ENDL = ";\n";

  static final List<TSType> REQUIRED_TYPES = Arrays.asList(
      TSType.of(java.lang.String.class).setExport(true),
      TSType.of(java.lang.Iterable.class).setExport(true).setFunctional(true),
      TSType.of(java.util.Iterator.class),
      TSType.of(java.util.Collection.class),
      TSType.of(java.util.List.class),
      TSType.of(java.util.Set.class),
      TSType.of(java.util.Map.class),
      TSType.of(java.util.Optional.class).setExport(true),
      TSType.of(java.util.stream.Stream.class).setExport(true),

      // Utility class(s)
      TSType.of(java.util.stream.Collectors.class).setExport(true),
      TSType.of(java.util.Collections.class).setExport(true),

      // Native functional interface(s)
      TSType.of(java.util.function.Function.class).setAlias("Func"),
      TSType.of(java.util.function.BiFunction.class).setAlias("BiFunction"),
      TSType.of(java.util.function.Consumer.class).setAlias("Consumer"),
      TSType.of(java.util.function.BiConsumer.class).setAlias("BiConsumer"),
      TSType.of(java.util.function.UnaryOperator.class).setAlias("UnaryOperator"),
      TSType.of(java.util.function.BinaryOperator.class).setAlias("BinaryOperator"),
      TSType.of(java.util.function.Supplier.class).setAlias("Supplier"),
      TSType.of(java.util.function.Predicate.class).setAlias("Predicate"),
      TSType.of(java.util.function.BiPredicate.class).setAlias("BiPredicate"),
      TSType.of(java.lang.Runnable.class),
      TSType.of(java.lang.Comparable.class)

      // Declare Functional Interface(s)
  );

  /**
   * Open a file for output.
   *
   * @param file     target file
   * @param header   header file to prepend to the output
   * @return         a writer for the output file
   * @throws IOException if an I/O error occurs
   */
  private java.io.Writer openFile(Path file, String header) throws IOException {

    final FileObject out = super.getSourceOutputFile(Paths.get("j2ts"), file);

    info("output file [%s]", out.getName());

    final java.io.Writer w = out.openWriter();

    try (final java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(header)) {
      int c;
      while ((c = is.read()) != -1) w.write(c);
    }

    return w;
  }
  @Override
  public boolean process(Context processingContext) throws Exception {

    final String targetDefinitionFile = processingContext.getOptionMap().getOrDefault("ts.outfile", "out");

    final String definitionsFile = targetDefinitionFile.concat(".d.ts");
    final String scriptFile = targetDefinitionFile.concat(".js");

    final String foreignObjectPrototype =
            processingContext.getOptionMap()
                    .getOrDefault( "foreignobjectprototype", "false");

    final String compatibilityOption =
        processingContext.getOptionMap()
            .getOrDefault("compatibility", "GRAALJS") ;
    info("COMPATIBILITY WITH [%s]", compatibilityOption);

    final Java2TSConverter converter = Java2TSConverter.builder()
                                                    .compatibility( compatibilityOption  )
                                                    .foreignObjectPrototype( foreignObjectPrototype )
                                                    .build();

    try (
        final java.io.Writer wD = openFile(Paths.get(definitionsFile), converter.isRhino() ? "headerD-rhino.ts" : "headerD.ts");
        final java.io.Writer wT = openFile(Paths.get(scriptFile), "headerT.ts");
    ) {

      final Consumer<String> wD_append = s -> {
        try {
          wD.append(s);
        } catch (IOException e) {
          error("error adding [%s]", s);
        }
      };
      final Consumer<String> wT_append = s -> {
        try {
          wT.append(s);
        } catch (IOException e) {
          error("error adding [%s]", s);
        }
      };

      final List<TSNamespace> namespaces = enumerateDeclaredPackageAndClass(processingContext);
			info( "==> detected namespaces");
			namespaces.forEach(ns -> info( String.valueOf(ns) ));
			info( "<== detected namespaces");
      final List<List<TSGlobal>> globals = enumerateDeclaredGlobals(processingContext);
            info( "==> detected globals");
            globals.forEach(ns -> info( String.valueOf(ns) ));
            info( "<== detected globals");
      final List<Tuple2<String, String>> prePostTypes = enumeratePrePostTypes(processingContext);
      final List<Tuple2<String, String>> prePostScripts = enumeratePrePostScripts(processingContext);

      final Set<TSType> types = new HashSet<>(PREDEFINED_TYPES);
      types.addAll(REQUIRED_TYPES);

      namespaces.forEach(ns -> types.addAll(ns.types()));

      final java.util.Map<String, TSType> declaredTypes =
          types.stream()
              .collect(Collectors.toMap(tt -> tt.getValue().getName(), tt -> tt));

      // Insert pre types
      prePostTypes.stream().map( t -> t.$0).forEach(wD_append);

      types.stream()
          .filter(tt -> !PREDEFINED_TYPES.contains(tt))
          .map(tt -> converter.javaClass2DeclarationTransformer(0, tt, declaredTypes))
          .sorted()
          .forEach(wD_append);

      wD_append.accept("\n\n// Globals\n");

      globals.stream().forEach(gl -> gl.stream().map( g-> String.format("declare const %s: %s;\n", g.name(), g.type().getName())).forEach(wD_append));

      // Insert post types
      prePostTypes.stream().map( t -> t.$1).forEach(wD_append);

      wT_append.accept(String.format("/// <reference path=\"%s\"/>\n\n", definitionsFile));
      prePostScripts.stream().map( t -> t.$0).forEach(wT_append);

      types.stream()
          .filter(TSType::isExport)
          .map(t -> converter.javaClass2StaticDefinitionTransformer(t, declaredTypes))
          .sorted()
          .forEach(wT_append);
      prePostScripts.stream().map( t -> t.$1).forEach(wT_append);

    } // end try-with-resources

    return true;
  }

  /**
   * Test if the given annotation is a {@link Java2TS}.
   *
   * @param am
   *            the annotation to test
   * @return true if the annotation is a {@link Java2TS}
   */
  private boolean isJava2TS(AnnotationMirror am) {

    info("'%s'='%s'", am.getAnnotationType().toString(), Java2TS.class.getName());
    return am.getAnnotationType().toString().equals(Java2TS.class.getName());

  }
  /**
   * Processes the given annotation to generate a typescript declaration
   *
   * @param am the annotation to process
   *
   * @return the typescript declaration
   */
  private TSNamespace toNamespace(AnnotationMirror am) {

    final Function<AnnotationValue, Set<TSType>> mapTypes = (value) ->
        ((List<? extends AnnotationValue>) value.getValue())
            .stream()
            .map(AnnotationValue::getValue)
            .filter(v -> v instanceof AnnotationMirror)
            .map(v -> toMapObject((AnnotationMirror) v, TSType::of))
            .collect(Collectors.toSet());

    final java.util.Map<? extends ExecutableElement, ? extends AnnotationValue> elementsValues = am.getElementValues();

    // elementsValues.entrySet().forEach( e -> System.out.printf( "===> elementValues.get('%s')=%s\n", e.getKey().getClass(), e.getValue()));

    final Set<TSType> types =
        elementsValues.entrySet().stream()
        .filter( e -> String.valueOf(e.getKey()).startsWith("declare"))
        .map(v -> mapTypes.apply(v.getValue()))
        .findFirst()
        .orElse(Collections.emptySet());

    // System.out.printf( "===> types size=%s\n", types.size());

    final String name = elementsValues.entrySet().stream()
        .filter( e -> String.valueOf(e.getKey()).equals("name"))
        .map(v -> String.valueOf(v.getValue()))
        .findFirst()
        .orElse("unnamed");

    return TSNamespace.of(name, types);

  }

  /**
   * Processes the given annotation to generate a typescript declaration
   *
   * @param am the annotation to process
   *
   * @return the typescript declaration
   */
  private List<TSGlobal> toGlobals(AnnotationMirror am) {

    final Function<AnnotationValue, List<TSGlobal>> mapConstants = (value) ->
            ((List<? extends AnnotationValue>) value.getValue())
                    .stream()
                    .filter(Objects::nonNull)
                    .map(AnnotationValue::getValue)
                    .filter(v -> v instanceof AnnotationMirror)
                    .map(v -> {
                      TSType m =  toMapObject((AnnotationMirror) v, TSType::of);
                      String name =  m.get("name") != null? (String)m.get("name") : "NULL";
                      Class<?> type;
                      Object dt =  m.get("type");
                      if (dt instanceof Class)
                        type= (Class<?>) dt;
                      final String fqn = dt.toString();
                      try {
                        type = Class.forName(fqn);

                      } catch (ClassNotFoundException e1) {
                        warn("class not found [%s]", dt);
                        type = java.lang.Object.class;
                      }
                      return TSGlobal.of(name, type);
                    })
                    .collect(Collectors.toList());
    final java.util.Map<? extends ExecutableElement, ? extends AnnotationValue> elementsValues = am.getElementValues();

//    elementsValues.entrySet().forEach( e -> info( "===> elementValues.get('%s')=%s\n", e.getKey().getClass(), e.getValue()));

    final List<TSGlobal> constants =
            elementsValues.entrySet().stream()
                    .filter( e -> String.valueOf(e.getKey()).startsWith("constants"))
                    .map(e ->  mapConstants.apply(e.getValue()))
                    .findFirst()
                    .orElse(List.of());

//    info( "===> constants size=%s\n", constants.size());
//    constants.forEach( e -> info( "===> constants.get('%s')=%s\n", e.name(), e.type().getName()));


    return constants;

  }

  /**
   *
   * @param processingContext
   * @return
   */
  private List<TSNamespace> enumerateDeclaredPackageAndClass(final Context processingContext) {

    return
        processingContext.elementFromAnnotations().stream()
            .peek(e -> info("Annotation [%s]", e.getKind().name()))
            .filter(e -> ElementKind.PACKAGE == e.getKind() || ElementKind.CLASS == e.getKind())
            .flatMap(e -> e.getAnnotationMirrors().stream().filter(this::isJava2TS))
            .map(this::toNamespace)
            .collect(Collectors.toList())
        ;
  }

  /**
   *
   * @param processingContext
   * @return
   */
  private List<List<TSGlobal>> enumerateDeclaredGlobals(final Context processingContext) {

    return
            processingContext.elementFromAnnotations().stream()
                    .peek(e -> info("Annotation [%s]", e.getKind().name()))
                    .filter(e -> ElementKind.PACKAGE == e.getKind() || ElementKind.CLASS == e.getKind())
                    .flatMap(e -> e.getAnnotationMirrors().stream().filter(this::isJava2TS))
                    .map(this::toGlobals)
                    .collect(Collectors.toList())
            ;
  }

  /**
   *
   * @param processingContext
   * @return
   */
  private List<Tuple2<String, String>> enumeratePrePostTypes(final Context processingContext) {

    return
            processingContext.elementFromAnnotations().stream()
                    .peek(e -> info("Annotation [%s]", e.getKind().name()))
                    .filter(e -> ElementKind.PACKAGE == e.getKind() || ElementKind.CLASS == e.getKind())
                    .flatMap(e -> e.getAnnotationMirrors().stream().filter(this::isJava2TS))
                    .map(am -> {
                      TSType m =  toMapObject(am, TSType::of);
                      return new Tuple2<String, String>((String)m.get("preTypes"),(String)m.get("postTypes") );
                    })
                    .collect(Collectors.toList())
            ;
  }

  private List<Tuple2<String, String>> enumeratePrePostScripts(final Context processingContext) {

    return
            processingContext.elementFromAnnotations().stream()
                    .peek(e -> info("Annotation [%s]", e.getKind().name()))
                    .filter(e -> ElementKind.PACKAGE == e.getKind() || ElementKind.CLASS == e.getKind())
                    .flatMap(e -> e.getAnnotationMirrors().stream().filter(this::isJava2TS))
                    .map(am -> {
                      TSType m =  toMapObject(am, TSType::of);
                      return new Tuple2<String, String>((String)m.get("preScript"),(String)m.get("postScript") );
                    })
                    .collect(Collectors.toList())
            ;
  }
}
