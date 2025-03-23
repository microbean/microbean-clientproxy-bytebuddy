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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;

import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeDescription.Generic.Builder;

import net.bytebuddy.pool.TypePool;

import static net.bytebuddy.description.type.TypeDescription.Generic.Builder.parameterizedType;
import static net.bytebuddy.description.type.TypeDescription.Generic.Builder.typeVariable;
import static net.bytebuddy.description.type.TypeDescription.Generic.Builder.unboundWildcard;

/**
 * A provider of {@link TypeDefinition}s backed by a {@link TypePool}, which is normally a {@link TypeElementTypePool}.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 */
public final class TypeDefinitions {

  private final TypePool typePool;

  /**
   * Creates a new {@link TypeDefinitions}.
   *
   * @param typePool a {@link TypePool} (normally a {@link TypeElementTypePool}); must not be {@code null}
   */
  public TypeDefinitions(final TypePool typePool) {
    super();
    this.typePool = Objects.requireNonNull(typePool, "typePool");
  }

  private final TypePool typePool() {
    return this.typePool;
  }

  /**
   * Returns a {@link TypeDescription} suitable for the supplied {@link TypeMirror}.
   *
   * @param t a {@link TypeMirror}; must be an array, primitive, void, or reference type
   *
   * @return a non-{@code null} {@link TypeDescription}
   *
   * @exception NullPointerException if {@code t} is {@code null}
   *
   * @exception IllegalArgumentException if {@code t} is an unsuitable {@link TypeMirror}
   */
  public final TypeDescription typeDescription(final TypeMirror t) {
    // Assumes t is thread safe, e.g. supplied via org.microbean.construct.Domain or similar
    return switch (t.getKind()) {
    case ARRAY -> TypeDescription.ArrayProjection.of(typeDescription(((ArrayType)t).getComponentType()));
    case DECLARED -> typeDescription(((QualifiedNameable)((DeclaredType)t).asElement()).getQualifiedName().toString()); // canonical name, not binary name
    case NONE -> null;

    // https://github.com/raphw/byte-buddy/blob/byte-buddy-1.16.0/byte-buddy-dep/src/main/java/net/bytebuddy/pool/TypePool.java#L540-L560
    case BOOLEAN -> typeDescription("boolean");
    case BYTE -> typeDescription("byte");
    case CHAR -> typeDescription("char");
    case DOUBLE -> typeDescription("double");
    case FLOAT -> typeDescription("float");
    case INT -> typeDescription("int");
    case LONG -> typeDescription("long");
    case SHORT -> typeDescription("short");
    case VOID -> typeDescription("void");

    case
      ERROR,
      EXECUTABLE,
      INTERSECTION,
      MODULE,
      NULL,
      OTHER,
      PACKAGE,
      TYPEVAR,
      UNION,
      WILDCARD -> throw new IllegalArgumentException("t: " + t +
                                                     "; kind: " +
                                                     t.getKind());
    };
  }

  /**
   * Returns a non-{@code null} {@link TypeDescription} for a <dfn>type pool name</dfn>.
   *
   * <p>The structure and format of type pool names are not fully documented and defined only by Byte Buddy's <a
   * href="https://github.com/raphw/byte-buddy/blob/master/byte-buddy-dep/src/main/java/net/bytebuddy/pool/TypePool.java#L535-L555">internal
   * implementation</a>.</p>
   *
   * @param typePoolName a type pool name; must not be {@code null}
   *
   * @return a non-{@code null} {@link TypeDescription}
   *
   * @exception NullPointerException if {@code typePoolName} is {@code null}
   *
   * @exception TypePool.Resolution.NoSuchTypeException if there is no suitable {@link TypeDescription}
   *
   * @see TypePool#describe(String)
   *
   * @see TypePool.Resolution#resolve()
   */
  public final TypeDescription typeDescription(final String typePoolName) {
    return this.typePool.describe(typePoolName).resolve();
  }

  /**
   * Returns a non-{@code null} {@link TypeDescription.Generic} for a {@link TypeMirror}.
   *
   * @param t a {@link TypeMirror}; must be an array, primitive, void, wildcard, or reference type
   *
   * @return a non-{@code null} {@link TypeDescription.Generic}
   *
   * @exception NullPointerException if {@code t} is {@code null}
   *
   * @exception IllegalArgumentException if {@code t} is not a suitable type
   */
  public final TypeDescription.Generic typeDescriptionGeneric(final TypeMirror t) {
    // Assumes t is thread safe, e.g. supplied via org.microbean.construct.Domain or similar
    return switch (t.getKind()) {

    // https://github.com/raphw/byte-buddy/blob/byte-buddy-1.16.0/byte-buddy-dep/src/main/java/net/bytebuddy/pool/TypePool.java#L540-L560
    case BOOLEAN -> typeDescription("boolean").asGenericType();
    case BYTE -> typeDescription("byte").asGenericType();
    case CHAR -> typeDescription("char").asGenericType();
    case DOUBLE -> typeDescription("double").asGenericType();
    case FLOAT -> typeDescription("float").asGenericType();
    case INT -> typeDescription("int").asGenericType();
    case LONG -> typeDescription("long").asGenericType();
    case SHORT -> typeDescription("short").asGenericType();
    case VOID -> typeDescription("void").asGenericType();

    case ARRAY -> Builder.of(typeDescriptionGeneric(((ArrayType)t).getComponentType())).asArray().build(); // recursive

    case DECLARED -> {
      final DeclaredType dt = (DeclaredType)t;
      final TypeElement te = (TypeElement)dt.asElement();
      final String n = te.getQualifiedName().toString(); // canonical name, not binary name

      final TypeDescription td = typeDescription(n);
      if (!generic(te)) {
        yield td.asGenericType();
      }

      final TypeMirror dtEnclosingType = dt.getEnclosingType();
      final TypeDescription.Generic enclosingType =
        dtEnclosingType == null ? TypeDescription.Generic.UNDEFINED : typeDescriptionGeneric(dtEnclosingType);

      final List<? extends TypeMirror> typeArgumentMirrors = dt.getTypeArguments();
      if (typeArgumentMirrors.isEmpty()) {
        yield parameterizedType(td, enclosingType).build();
      }
      final List<TypeDefinition> typeArguments = new ArrayList<>(typeArgumentMirrors.size());
      for (final TypeMirror typeArgumentMirror : typeArgumentMirrors) {
        typeArguments.add(typeDescriptionGeneric(typeArgumentMirror));
      }

      yield parameterizedType(td,
                              enclosingType == null ? TypeDescription.Generic.UNDEFINED : enclosingType,
                              typeArguments)
        .build();
    }

    case TYPEVAR -> typeVariable(((TypeVariable)t).asElement().getSimpleName().toString()).build();

    case WILDCARD -> {
      final WildcardType w = (WildcardType)t;
      final TypeMirror extendsBound = w.getExtendsBound();
      final TypeMirror superBound = w.getSuperBound();
      if (superBound == null) {
        if (extendsBound == null) {
          yield unboundWildcard();
        }
        yield Builder.of(typeDescriptionGeneric(extendsBound)).asWildcardUpperBound();
      } else if (extendsBound == null) {
        yield Builder.of(typeDescriptionGeneric(superBound)).asWildcardLowerBound();
      } else {
        throw new AssertionError();
      }
    }

    case NONE -> null;

    case
      ERROR,
      EXECUTABLE,
      INTERSECTION,
      MODULE,
      NULL,
      OTHER,
      PACKAGE,
      UNION -> throw new IllegalArgumentException("t: "
                                                  + t +
                                                  "; kind: " +
                                                  t.getKind());
    };
  }

  private static final boolean generic(final TypeElement te) {
    // Assumes t is thread safe, e.g. supplied via org.microbean.construct.Domain or similar
    return switch (te.getKind()) {
    case CLASS, CONSTRUCTOR, ENUM, INTERFACE, METHOD, RECORD -> !te.getTypeParameters().isEmpty();
    default -> false;
    };
  }

}
