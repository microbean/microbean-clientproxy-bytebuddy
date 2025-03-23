/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2025 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.microbean.clientproxy.bytebuddy;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import net.bytebuddy.ByteBuddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;

import net.bytebuddy.description.modifier.FieldManifestation;
import net.bytebuddy.description.modifier.MethodManifestation;
import net.bytebuddy.description.modifier.ParameterManifestation;
import net.bytebuddy.description.modifier.TypeManifestation;

import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;

import net.bytebuddy.dynamic.DynamicType;

import net.bytebuddy.implementation.DefaultMethodCall;
import net.bytebuddy.implementation.HashCodeMethod;
import net.bytebuddy.implementation.EqualsMethod;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodCall;

import net.bytebuddy.implementation.bytecode.assign.Assigner;

import net.bytebuddy.matcher.ElementMatcher;

import net.bytebuddy.pool.TypePool;

import static net.bytebuddy.description.modifier.Ownership.STATIC;
import static net.bytebuddy.description.modifier.SyntheticState.SYNTHETIC;
import static net.bytebuddy.description.modifier.Visibility.PRIVATE;
import static net.bytebuddy.description.modifier.Visibility.PUBLIC;

import static net.bytebuddy.description.type.TypeDescription.Generic.Builder.parameterizedType;
import static net.bytebuddy.description.type.TypeDescription.Generic.Builder.typeVariable;

import static net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy.Default.NO_CONSTRUCTORS;

import static net.bytebuddy.implementation.MethodCall.invoke;
import static net.bytebuddy.implementation.MethodCall.invokeSelf;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.hasParameters;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.isEquals;
import static net.bytebuddy.matcher.ElementMatchers.isFinal;
import static net.bytebuddy.matcher.ElementMatchers.isHashCode;
import static net.bytebuddy.matcher.ElementMatchers.isPackagePrivate;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isToString;
import static net.bytebuddy.matcher.ElementMatchers.isVirtual;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

/**
 * An class generator that uses <a href="https://bytebuddy.net/#/">Byte Buddy</a> to {@linkplain #generate(String,
 * TypeDefinition, Collection) generate} {@linkplain org.microbean.reference.ClientProxy client proxy} classes.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public final class BBClientProxyClassGenerator {

  private final TypePool typePool;

  /**
   * Creates a new {@link BBClientProxyClassGenerator}.
   *
   * @param typePool a {@link TypePool} (normally a {@link TypeElementTypePool}); must not be {@code null}
   *
   * @exception NullPointerException if {@code typePool} is {@code null}
   */
  public BBClientProxyClassGenerator(final TypePool typePool) {
    super();
    this.typePool = Objects.requireNonNull(typePool, "typePool");
  }

  /**
   * Creates and returns a new {@link DynamicType.Unloaded} representing a client proxy class.
   *
   * @param name the name of the client proxy class; must not be {@code null}; must be a valid Java class <a 
   * href="https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/lang/ClassLoader.html#binary-name">binary
   * name</a>
   *
   * @param superclass a {@link TypeDefinition} representing a superclass; must not be {@code null}
   *
   * @param interfaces a {@link Collection} of {@link TypeDefinition}s representing interfaces the client proxy class
   * will implement; must not be {@code null}
   *
   * @return a new, non-{@code null} {@link DynamicType.Unloaded} representing a client proxy class
   *
   * @exception NullPointerException if any argument is {@code null}
   */
  public final DynamicType.Unloaded<?> generate(final String name,
                                                final TypeDefinition superclass,
                                                final Collection<? extends TypeDefinition> interfaces) {

    // ClientProxy<Superclass>
    final TypeDescription.Generic clientProxyType =
      parameterizedType(this.typeDescription("org.microbean.reference.ClientProxy"),
                        List.of(superclass))
      .build();

    // Supplier<? extends Superclass>
    final TypeDescription.Generic supplierType =
      parameterizedType(this.typeDescription("java.util.function.Supplier"),
                        List.of(TypeDescription.Generic.Builder.of(superclass.asGenericType()).asWildcardUpperBound()))
      .build();

    // public final class Name extends Superclass implements ClientProxy<Superclass>, Interfaces { /* ... */ }
    DynamicType.Builder<?> builder = new ByteBuddy()
      .subclass(superclass, NO_CONSTRUCTORS)
      .merge(List.of(PUBLIC, SYNTHETIC, TypeManifestation.FINAL))
      .name(name)
      .implement(clientProxyType)
      .implement(interfaces)

      // private final Supplier<? extends Superclass> $proxiedSupplier;
      .defineField("$proxiedSupplier", supplierType, PRIVATE, SYNTHETIC, FieldManifestation.FINAL)

      // public Name(final Supplier<? extends Superclass> proxiedSupplier) {
      //   super();
      //   Objects.requireNonNull(proxiedSupplier, "proxiedSupplier");
      //   this.$proxiedSupplier = proxiedSupplier;
      // }
      .defineConstructor(PUBLIC, SYNTHETIC)
      .withParameter(supplierType, "proxiedSupplier", ParameterManifestation.FINAL)
      .intercept(invoke(superclass.getDeclaredMethods().filter(isConstructor().and(takesNoArguments())).getOnly())
                 .andThen(invoke(this.typeDescription("java.util.Objects")
                                 .getDeclaredMethods()
                                 .filter(named("requireNonNull")
                                         .and(takesArgument(1, this.typeDescription("java.lang.String"))))
                                 .getOnly())
                          .withArgument(0)
                          .with("proxiedSupplier"))
                 .andThen(FieldAccessor.ofField("$proxiedSupplier").setsArgumentAt(0)))

      // @Override // ClientProxy<Superclass>
      // public final Superclass $proxied() {
      //   return this.$proxiedSupplier.get();
      // }
      .defineMethod("$proxied", superclass, PUBLIC, SYNTHETIC, MethodManifestation.FINAL)
      .intercept(invoke(named("get"))
                 .onField("$proxiedSupplier")
                 .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC))

      // @Override // ClientProxy<Superclass>
      // public final Superclass $cast() {
      //   return ClientProxy.super.$cast();
      // }
      .defineMethod("$cast", superclass, PUBLIC, SYNTHETIC, MethodManifestation.FINAL)
      .intercept(DefaultMethodCall.prioritize(clientProxyType.asErasure()))

      // Existing/inherited methods; remember that they form a stack, so the last .method() call below should be the
      // most specific. See https://bytebuddy.net/#members for details.

      // @Override // Superclass/interfaces
      // public Bar foo() {
      //   return $proxied().foo(); // so long as foo() meets certain requirements
      // }
      .method(isBusinessMethod()
              .and(not(isJavaDeclaredMethod()
                       .and(isPackagePrivate()
                            .or(hasOnePackagePrivateParameter())))))
      .intercept(invokeSelf()
                 .onMethodCall(invoke(named("$proxied")))
                 .withAllArguments())

      // @Override // Superclass, Object
      // public final boolean equals(final Object other) {
      //   if (other == this) {
      //     return true;
      //   } else if (other != null && other.getClass() == this.getClass()) {
      //     return this.$proxiedSupplier == ((Name)other).$proxiedSupplier;
      //   } else {
      //     return false;
      //   }
      // }
      .method(isEquals())
      .intercept(EqualsMethod.isolated()
                 .withIdentityFields(any()) // there's only one
                 .withNonNullableFields(any()))

      // @Override // Superclass, Object
      // public int hashCode() {
      //   int offset = 31;
      //   return offset * 17 + this.$proxiedSupplier.hashCode(); // or similar
      // }
      .method(isHashCode())
      .intercept(HashCodeMethod.usingOffset(31)
                 .withIdentityFields(any())
                 .withNonNullableFields(any())
                 .withMultiplier(17)) // see https://github.com/raphw/byte-buddy/issues/1764

      // @Override // Superclass/interfaces/Object
      // public String toString() {
      //   return $proxied().toString();
      // }
      .method(isToString())
      .intercept(invoke(named("toString"))
                 .onMethodCall(invoke(named("$proxied"))));

    return builder.make(this.typePool);
  }


  /*
   * Static methods.
   */


  private static final ElementMatcher<MethodDescription> hasOnePackagePrivateParameter() {
    return m -> {
      for (final ParameterDescription pd : m.getParameters()) {
        if (isPackagePrivate().matches(pd.getType())) {
          return true;
        }
      }
      return false;
    };
  }

  private static final ElementMatcher.Junction<MethodDescription> isBusinessMethod() {
    return isVirtual()
      .and(not(isFinal()))
      .and(not(isDeclaredBy(Object.class)));
  }

  private static final ElementMatcher.Junction<MethodDescription> isJavaDeclaredMethod() {
    return isDeclaredBy(typeNameStartsWith("java."));
  }

  private final TypeDescription typeDescription(final String canonicalName) {
    return this.typePool.describe(canonicalName).resolve();
  }

  private static final ElementMatcher<TypeDefinition> typeNameStartsWith(final String prefix) {
    Objects.requireNonNull(prefix, "prefix");
    return t -> t.getTypeName().startsWith(prefix);
  }

}
