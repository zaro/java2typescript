package org.bsc.java2typescript;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static java.lang.String.format;
import java.util.Arrays;

/**
 * 
 * @author bsorrentino
 *
 */
public class TSType extends HashMap<String, Object> {

    private static final String ALIAS = "alias";
    private static final String VALUE = "value";
    private static final String EXPORT = "export";
    private static final String NAMESPACE = "namespace";
    private static final String FUNCTIONAL = "functional";
    private static final String PRE = "pre";
    private static final String POST = "post";

    protected TSType() {
        super(3);
    }


    public static TSType of() {
        return new TSType() {
            {
                put(VALUE, Void.class);
            }
        };
    }
    public static TSType of(Class<?> cl) {
        return new TSType() {
            {
                put(VALUE, cl);
            }
        };
    }

    /**
     * 
     * @return
     */
    public Class<?> getValue() {
        return getClassFrom(super.get(VALUE));
    }

    /**
     * 
     * @return
     */
    public boolean isExport() {
        return (boolean) super.getOrDefault(EXPORT, false);
    }

    /**
     * 
     * @return
     */
    public TSType setExport(boolean value) {
        super.put(EXPORT, value);
        return this;
    }

    /**
     * 
     * @return
     */
    public boolean hasAlias() {
        final String alias = (String) super.get(ALIAS);
        return alias != null && !alias.isEmpty();
    }

    /**
     * 
     * @return
     */
    public String getAlias() {
        return (String) super.get(ALIAS);
    }

       /**
     * 
     * @return
     */
    public TSType setAlias( String value ) {
        super.put(ALIAS,value);
        return this;
    }

    /**
     * Test is functional interface
     * 
     * @return
     */
    public boolean isFunctional() {

        
        if( !getValue().isInterface()) return false;
        if( getValue().isAnnotationPresent(FunctionalInterface.class)) return true;
        
        return (Boolean)super.getOrDefault( FUNCTIONAL, false) && 
                Arrays.stream(getValue().getDeclaredMethods())
                    .filter( m -> Modifier.isAbstract(m.getModifiers()) )
                    .count() == 1;
    }

    /**
     * Test is functional interface
     *
     * @return
     */
    public boolean isAbstract() {


        return Modifier.isAbstract(getValue().getModifiers());
    }

    /**
     * 
     */
    public TSType setFunctional( boolean value ) {

        super.put(FUNCTIONAL, value);
        return this;

    }

    /**
     *
     * @return
     */
    public String getPre() {
        return (String) super.getOrDefault(PRE, "");
    }

    /**
     *
     * @return
     */
    public TSType setPre(String value) {
        super.put(PRE, value);
        return this;
    }

    /**
     *
     * @return
     */
    public String getPost() {
        return (String) super.getOrDefault(POST, "");
    }

    /**
     *
     * @return
     */
    public TSType setPost(String value) {
        super.put(POST, value);
        return this;
    }

    
    private String getMemberSimpleTypeName() {

        return format( "%s$%s", getValue().getDeclaringClass().getSimpleName(), getValue().getSimpleName());
    }

    /**
     * 
     * @return
     */
    public final String getTypeName() {
        return (hasAlias()) ? getAlias() : format( "%s.%s", getNamespace(), 
                (getValue().isMemberClass() ? getMemberSimpleTypeName() : getValue().getSimpleName()));
    }

    /**
     * 
     * @return
     */
    public final String getSimpleTypeName() {
        return (hasAlias()) ? getAlias() : 
            ((getValue().isMemberClass()) ? getMemberSimpleTypeName() : getValue().getSimpleName());
    }

    /**
     * 
     * @return
     */
    public final boolean supportNamespace() {
        return !hasAlias();
    }
    
    /**
     * 
     * @return
     */
    public final String getNamespace() {
        return (String) super.getOrDefault(NAMESPACE, getValue().getPackage().getName());
    }
    
    /**
     *
     * @return
     */
    public Set<Field> getFields() {

        final Predicate<Field> std = f -> !f.isSynthetic() && Modifier.isPublic(f.getModifiers())
                && Character.isJavaIdentifierStart(f.getName().charAt(0))
                && f.getName().chars().skip(1).allMatch(Character::isJavaIdentifierPart);

        return Stream.concat(Stream.of(getValue().getFields()), Stream.of(getValue().getDeclaredFields())).filter(std)
                .collect(Collectors.toSet());

    }

    /**
     *
     * @param m
     * @return
     */
    protected boolean testIncludeMethod( Method m ) {
        return !m.isBridge() && !m.isSynthetic() && Modifier.isPublic(m.getModifiers())
                && Character.isJavaIdentifierStart(m.getName().charAt(0))
                && m.getName().chars().skip(1).allMatch(Character::isJavaIdentifierPart);
    }

    /**
     *
     * @param f
     * @return
     */
    protected boolean testIncludeField( Field f ) {
        return !f.isSynthetic() && Modifier.isPublic(f.getModifiers())
                && Character.isJavaIdentifierStart(f.getName().charAt(0))
                && f.getName().chars().skip(1).allMatch(Character::isJavaIdentifierPart);
    }


    /**
     *
     * @return
     */
    public Stream<Method> getMethodsAsStream() {

        return Stream.concat(
                        Stream.of(getValue().getMethods()),
                        Stream.of(getValue().getDeclaredMethods()))
                .filter(this::testIncludeMethod);
    }

    /**
     *
     * @return
     */
    public final Set<Method> getMethods() {
        return getMethodsAsStream().collect(Collectors.toSet());
    }

    /**
     *
     * @return
     */
    public Stream<Field> getPublicFieldsAsStream() {

        return Stream.concat(
                        Stream.of(getValue().getFields()),
                        Stream.of(getValue().getDeclaredFields()))
                .filter(this::testIncludeField);
    }

    /**
     * 
     * @return
     */
    private Class<?> getMemberClassForName( String fqn ) throws ClassNotFoundException {
        char[] ch = fqn.toCharArray();
        int i = fqn.lastIndexOf('.');
        ch[i] = '$';

        return Class.forName(String.valueOf(ch));   
    }
    
    /**
     *
     * @param dt
     * @return
     */
    private Class<?> getClassFrom(Object dt) {
        if (dt instanceof Class)
            return (Class<?>) dt;

        final String fqn = dt.toString();
        try {
            return Class.forName(fqn);

        } catch (ClassNotFoundException e1) {

            try {
                return getMemberClassForName(fqn);
    
            } catch (ClassNotFoundException e2) {
                throw new RuntimeException(String.format("class not found [%s]", dt), e1);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if( o instanceof Class ) {
            return getValue().equals(o);
        }
        if( o instanceof TSType ) {
            return getValue().equals(((TSType) o).getValue());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getValue().hashCode();
    }

    @Override
    public String toString() {
        return format("TSType{ value: %s }", getValue().getName());
    }
}
