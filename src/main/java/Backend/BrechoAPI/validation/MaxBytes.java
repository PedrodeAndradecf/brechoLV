package Backend.BrechoAPI.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;













@Documented
@Constraint(validatedBy = MaxBytesValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface MaxBytes {

    int value();

    String message() default "excede o limite de bytes permitido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
