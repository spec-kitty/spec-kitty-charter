package ai.speckitty.charter.kernel.transport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an operation the contract declares in full but reserves (ADR-0020 D8): it carries {@code x-reserved} and
 * answers {@code 501 Not Implemented}, a {@code Problem} with no code, until it is built. The composition root's
 * interceptor answers for a marked operation before its request is bound, so no body binding, bean validation or
 * conversion answers first.
 *
 * <p>On a method, only that operation is reserved; on a type, every handler method of it.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ReservedOperation {}
