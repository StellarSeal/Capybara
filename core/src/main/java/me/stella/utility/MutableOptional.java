package me.stella.utility;

import java.util.Optional;
import java.util.function.Supplier;

public class MutableOptional<T> {

   private T object;

   public MutableOptional(T object) {
      this.object = object;
   }

   public static <T> MutableOptional<T> of(T object) {
      return new MutableOptional<>(object);
   }

   public static <T> MutableOptional<T> empty() {
      return new MutableOptional<>(null);
   }

   public boolean isSet() {
      return object != null;
   }

   public void set(T object) {
      this.object = object;
   }

   public T get() {
      return object;
   }

   public T provide(Supplier<T> provider) {
      if (!isSet()) {
         object = provider.get();
      }
      return object;
   }

   public Optional<T> asOptional() {
      return Optional.ofNullable(object);
   }

   public void clear() {
      object = null;
   }

   @Override
   public String toString() {
      return isSet() ? String.valueOf(object) : "";
   }
}
