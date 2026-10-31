package ai.speckitty.charter.packs.infra.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller whose operations the contract declares in full but reserves (ADR-0020 D8): they carry
 * {@code x-reserved} and answer {@code 501 Not Implemented}, a {@code Problem} with no code, until they are built.
 * {@link ReservedOperationInterceptor} answers for every handler method of a marked controller before its request is
 * bound, so no body binding, bean validation or conversion answers first.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ReservedOperation {}
