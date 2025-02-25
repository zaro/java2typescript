package org.bsc.java2typescript.transformer;

import org.bsc.java2typescript.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.bsc.java2typescript.Java2TSConverter.Compatibility.GRAALJS;

/**
 *
 */
public class TSJavaClass2DeclarationTransformer extends TSConverterStatic implements TSTransformer {

    /**
     *
     * @param md
     * @return
     */
    protected boolean testMethodNotAllowed(Method md ) {
        final String name = md.getName();
        return !(name.contains("$")     || // remove unnamed
                name.equals("getClass") ||
                name.equals("hashCode") ||
                name.equals("wait")     ||
                name.equals("notify")   ||
                name.equals("notifyAll"))
                ;
    }


    /**
     *
     * @param md
     * @return
     */
    private boolean testMethodsNotAllowedInForeignObjectPrototypeOnList( Method md ) {
        final String name = md.getName();

        return !(name.equals("forEach")      ||
                name.equals("indexOf")      ||
                name.equals("lastIndexOf")  ||
                name.equals("sort"))
                ;
    }

    /**
     *
     * @param ctx
     * @return
     */
    private boolean isForeignObjectPrototypeOptionEnabled(TSConverterContext ctx) {
        return ctx.options.compatibility == GRAALJS &&
                ctx.options.foreignObjectPrototype;
    }

    /**
     *
     * @param ctx
     * @return
     */
    protected Stream<Method> getMethodsAsStream(TSConverterContext ctx) {

        if(  isForeignObjectPrototypeOptionEnabled(ctx) &&
                ctx.type.getValue().equals(java.util.List.class)) {

            return ctx.type.getMethodsAsStream()
                    .filter( this::testMethodsNotAllowedInForeignObjectPrototypeOnList );
        }

        return ctx.type.getMethodsAsStream();
    }


    /**
     *
     * @param ctx
     * @return
     */
    protected TSConverterContext getClassDecl(TSConverterContext ctx) {

        if(  isForeignObjectPrototypeOptionEnabled(ctx) &&
                ctx.type.getValue().equals(java.util.List.class)) {
            return ctx.append("interface List<E> extends Array<E>/* extends Collection<E> */ {");
        }

        return ctx.getClassDecl();
    }

    protected TSConverterContext appendPublicFields(TSConverterContext ctx, boolean staticFields) {
        final Set<Field> fields = getPublicFieldsAsStream(ctx).collect(Collectors.toSet());
        fields.stream()
                .filter( md -> isStatic(md) == staticFields)
                .filter( this::testFieldNotAllowed)
                .map( md -> ctx.getFieldDecl(md, false /* optional */, isStatic(md)) )
                .sorted()
                .forEach( decl -> ctx.append('\t').append(decl).append(ENDL));

        return ctx;
    }

    protected TSConverterContext appendStaticInterface(TSConverterContext ctx) {
        String staticInterfaceName = ctx.type.getSimpleTypeName() + "Static";
        ctx.append("interface ").append(staticInterfaceName).append(" {\n\n");

        if (ctx.type.getValue().isEnum()) {
            ctx.processEnumType();
        }

        // Append class property
        ctx.append("\treadonly class:any;\n");

        // Append static fields
        appendPublicFields(ctx, true);

        if (ctx.type.isFunctional()) {

            final java.util.Set<String> TypeVarSet = new java.util.HashSet<>(5);
            final String tstype = convertJavaToTS(ctx.type.getValue(), ctx.type, ctx.declaredTypeMap, false,
                    Optional.of((tv) -> TypeVarSet.add(tv.getName())));

            ctx.append("\tnew");
            if (!TypeVarSet.isEmpty()) {
                ctx.append('<').append(TypeVarSet.stream().collect(Collectors.joining(","))).append('>');
            }
            ctx.append("( arg0:").append(tstype).append(" ):").append(tstype).append(ENDL);

        } else {

            Stream.of(ctx.type.getValue().getConstructors()).filter(c -> Modifier.isPublic(c.getModifiers()))
                    .forEach(c -> {
                        ctx.append("\tnew").append(ctx.getMethodParametersAndReturnDecl(c, false)).append(ENDL);
                    });

            final java.util.Set<Method> methodSet = ctx.type.getMethods().stream().filter(Java2TSConverter::isStatic)
                    .collect(Collectors.toCollection(() -> new java.util.LinkedHashSet<>()));

            if (!methodSet.isEmpty()) {

                methodSet.stream().sorted(Comparator.comparing(Method::toGenericString)).forEach(md -> ctx.append('\t')
                        .append(md.getName()).append(ctx.getMethodParametersAndReturnDecl(md, false)).append(ENDL));
            }

        }
        ctx.append("\n} // end ").append(staticInterfaceName).append('\n');

        return ctx;
    }

    /**
     *
     * @param ctx
     * @return
     */
    public TSConverterContext apply(TSConverterContext ctx) {

        final TSType tstype = ctx.type;

        final Set<Method> methods = getMethodsAsStream(ctx).collect(Collectors.toSet());;

        if (tstype.supportNamespace())
            ctx.append("declare namespace ")
                .append(tstype.getNamespace()).append(" {\n\n");

        if(tstype.getPre() !=null){
            ctx.append(tstype.getPre());
        }

        getClassDecl(ctx).append("\n\n");

        // Add public fields
        appendPublicFields(ctx, false);

        if (tstype.isFunctional()) {

            methods.stream()
                    .filter( m -> Modifier.isAbstract(m.getModifiers()))
                    .findFirst()
                    .ifPresent( m -> ctx.append('\t')
                                    .append(ctx.getMethodParametersAndReturnDecl(m, false))
                                    // Rhino compatibility ???
                                    //.append("\n\t")
                                    //.append(getMethodDecl(ctx, m, false /* non optional */))
                                    .append(ENDL));

            methods.stream()
                    .filter( m -> !Modifier.isAbstract(m.getModifiers()))
                    .map( m -> ctx.getMethodDecl(m, true /* optional */))
                    .sorted()
                    .forEach( decl -> ctx.append('\t')
                            .append(decl)
                            .append(ENDL));

        } else {

            ctx.processEnumDecl();

            methods.stream()
                .filter( md -> (tstype.isExport() && isStatic(md)) == false)
                .filter( this::testMethodNotAllowed)
                .map( md -> ctx.getMethodDecl(md, false /* optional */) )
                .sorted()
                .forEach( decl -> ctx.append('\t').append(decl).append(ENDL));
        }

        ctx.append("\n} // end ").append(tstype.getSimpleTypeName()).append('\n');

        appendStaticInterface(ctx);
        // NESTED CLASSES
        // if( level == 0 ) ctx.processMemberClasses( level );

        if(tstype.getPost() !=null){
            ctx.append(tstype.getPost());
        }

        if (tstype.supportNamespace())
            ctx.append("\n} // end namespace ").append(tstype.getNamespace()).append('\n');

        // If exported output const
        if(tstype.isExport()) {
                ctx.append("declare const ")
                        .append(ctx.type.getSimpleTypeName())
                        .append(": ");
                if(ctx.type.isAbstract()){
                    ctx.append(ctx.type.getTypeName());
                }else {
                    ctx.append(ctx.type.getValue().getName())
                            .append("Static");
                }
                        ctx.append(ENDL)
                        .append("\n\n");
        }

        return ctx;
    }
}
