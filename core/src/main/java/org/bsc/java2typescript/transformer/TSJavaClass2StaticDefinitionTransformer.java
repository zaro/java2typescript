package org.bsc.java2typescript.transformer;

import org.bsc.java2typescript.Java2TSConverter;
import org.bsc.java2typescript.TSConverterContext;
import org.bsc.java2typescript.TSConverterStatic;
import org.bsc.java2typescript.TSTransformer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * transform java class to Typescript Static definition
 *
 */
public class TSJavaClass2StaticDefinitionTransformer extends TSConverterStatic implements TSTransformer {
    @Override
    public TSConverterContext apply(TSConverterContext ctx) {


        ctx.append("export const ")
                .append(ctx.type.getSimpleTypeName())
                .append(" = ");
        if(ctx.type.isAbstract()){
            ctx.append(ctx.getOptions().compatibility.javaExtend(ctx.type.getValue().getName()));

        }else {
                ctx.append(ctx.getOptions().compatibility.javaType(ctx.type.getValue().getName()));
        }
         ctx.append(ENDL)
                .append("\n\n");

        return ctx;
    }
}
