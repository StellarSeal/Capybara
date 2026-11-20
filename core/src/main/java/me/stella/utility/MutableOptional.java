package me.stella.utility;

import java.util.Optional;
import java.util.function.Supplier;

public class MutableOptional<T> {
   private T object;

   public MutableOptional(T object) {
      this.set(object);
   }

   public static <T> MutableOptional<T> of(T object) {
      return new MutableOptional<T>(object);
   }

   public static <T> MutableOptional<T> empty() {
      return new MutableOptional<T>(null);
   }

   public boolean isSet() {
      return this.object != null;
   }

   public void set(T object) {
      this.object = object;
   }

   public T get() {
      return this.object;
   }

   public T provide(Supplier<T> provider) {
      if (!this.isSet()) {
         this.set(provider.get());
      }

      return this.object;
   }

   public Optional<T> asOptional() {
      return Optional.ofNullable(this.object);
   }

   public void clear() {
      this.set(null);
   }

   public String toString() {
      return !this.isSet() ? "" : String.valueOf(this.object);
   }
}
