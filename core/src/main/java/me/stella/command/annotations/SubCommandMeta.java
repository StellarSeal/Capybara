package me.stella.command.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a sub-command of the root command named {@link #root()}; discovered automatically. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubCommandMeta {
    /** Name of the owning root command. */
    String root();

    String name();

    String[] aliases() default {};

    String description() default "";

    /** Argument syntax after the sub-command name, e.g. {@code "<world>"}. */
    String usage() default "";

    String permission() default "";

    boolean playerOnly() default false;

    /** Minimum number of arguments after the sub-command name; fewer prints the usage. */
    int minArgs() default 0;
}
