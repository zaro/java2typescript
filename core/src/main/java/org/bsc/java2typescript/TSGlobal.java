package org.bsc.java2typescript;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

public record TSGlobal(String name, Class<?> type ) {

    public static TSGlobal of(String name, Class<?> type ) {
        return new TSGlobal( name, type );
    }

    @Override
    public String toString() {
        return format( "TSGlobal: { name: '%s', type: %s  }",
            name, type.getName() );
    }
}
