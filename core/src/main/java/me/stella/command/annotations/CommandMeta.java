package me.stella.command.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a root command; picked up by {@link me.stella.command.CommandDiscovery}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandMeta {
    String name();

    String description() default "";

    String usage() default "";

    String[] aliases() default {};

    String permission() default "";

    boolean playerOnly() default false;
}
