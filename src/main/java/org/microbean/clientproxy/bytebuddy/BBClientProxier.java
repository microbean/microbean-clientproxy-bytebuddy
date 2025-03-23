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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import java.util.Objects;

import net.bytebuddy.dynamic.DynamicType;

import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

import net.bytebuddy.pool.TypePool;

import org.microbean.construct.Domain;

import org.microbean.reference.AbstractClientProxier;
import org.microbean.reference.ProxySpecification;

/**
 * An {@link AbstractClientProxier} that uses <a href="https://bytebuddy.net/#/">Byte Buddy</a> to {@linkplain
 * #generate(ProxySpecification) generate} {@linkplain org.microbean.reference.ClientProxy client proxies}.
 *
 * @author <a href="https://about.me/lairdnelson" target="_top">Laird Nelson</a>
 *
 * @see BBClientProxyClassGenerator
 */
public final class BBClientProxier extends AbstractClientProxier<DynamicType.Unloaded<?>> {

  private final TypeDefinitions tds;

  private final BBClientProxyClassGenerator g;

  private static final Lookup lookup = MethodHandles.lookup(); // or instance variable?

  /**
   * Creates a new {@link BBClientProxier}.
   *
   * @param domain a {@link Domain}; must not be {@code null}
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @see TypeElementTypePool#TypeElementTypePool(Domain)
   *
   * @see #BBClientProxier(Domain, TypePool)
   */
  public BBClientProxier(final Domain domain) {
    this(domain, new TypeElementTypePool(domain));
  }

  /**
   * Creates a new {@link BBClientProxier}.
   *
   * @param domain a {@link Domain}; must not be {@code null}
   *
   * @param typePool a {@link TypePool}; must not be {@code null}
   *
   * @exception NullPointerException if any argument is {@code null}
   *
   * @see TypeDefinitions#TypeDefinitions(TypePool)
   *
   * @see BBClientProxyClassGenerator#BBClientProxyClassGenerator(TypePool)
   *
   * @see #BBClientProxier(Domain, TypeDefinitions, BBClientProxyClassGenerator)
   */
  public BBClientProxier(final Domain domain,
                         final TypePool typePool) {
    this(domain, new TypeDefinitions(typePool), new BBClientProxyClassGenerator(typePool));
  }

  /**
   * Creates a new {@link BBClientProxier}.
   *
   * @param domain a {@link Domain}; must not be {@code null}
   *
   * @param tds a {@link TypeDefinitions}; must not be {@code null}
   *
   * @param g a {@link BBClientProxyClassGenerator}; must not be {@code null}
   *
   * @exception NullPointerException if any argument is {@code null}
   */
  public BBClientProxier(final Domain domain,
                         final TypeDefinitions tds,
                         final BBClientProxyClassGenerator g) {
    super(domain);
    this.tds = Objects.requireNonNull(tds, "tds");
    this.g = Objects.requireNonNull(g, "g");
  }

  @Override // AbstractClientProxier<DynamicType.Unloaded<?>>
  protected final DynamicType.Unloaded<?> generate(final ProxySpecification ps) {
    return
      this.g.generate(ps.name(),
                      this.tds.typeDescription(ps.superclass()),
                      ps.interfaces().stream().map(this.tds::typeDescriptionGeneric).toList());
  }

  @Override // AbstractClientProxier<DynamicType.Unloaded<?>>
  protected final Class<?> clientProxyClass(final DynamicType.Unloaded<?> dtu, final ClassLoader cl)
    throws ClassNotFoundException {
    // getTypeName() invoked on a TypeDescription will be its binary name (required by Class#forName(String)):
    // https://javadoc.io/static/net.bytebuddy/byte-buddy/1.17.3/net/bytebuddy/description/type/TypeDefinition.html#getTypeName--
    final String binaryName = dtu.getTypeDescription().getSuperClass().asErasure().getTypeName();
    final Class<?> superclass = Class.forName(binaryName, false, cl);
    return dtu.load(superclass.getClassLoader(), ClassLoadingStrategy.UsingLookup.of(lookup(superclass))).getLoaded();
  }

  @Override // AbstractClientProxier<DynamicType.Unloaded<?>>
  protected final Lookup lookup(final Class<?> c) {
    return lookup.in(c);
  }

}
