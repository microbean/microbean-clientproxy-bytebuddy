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

import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodHandleDesc;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.microbean.attributes.Attributes;

import org.microbean.assign.AttributedType;
import org.microbean.assign.Selectable;
import org.microbean.assign.Selectables;

import org.microbean.bean.Bean;
import org.microbean.bean.BeanQualifiersMatcher;
import org.microbean.bean.BeanTypeList;
import org.microbean.bean.BeanTypeMatcher;
import org.microbean.bean.BeanTypes;
import org.microbean.bean.Beans;
import org.microbean.bean.Constant;
import org.microbean.bean.Id;
import org.microbean.bean.IdMatcher;
import org.microbean.bean.Qualifiers;

import org.microbean.construct.DefaultDomain;
import org.microbean.construct.Domain;

import org.microbean.producer.InterceptorBindingsMatcher;

import org.microbean.proxy.Proxy;

import org.microbean.reference.Instances;
import org.microbean.reference.Request;

import org.microbean.scopelet.MapBackedScopelet;
import org.microbean.scopelet.NoneScopelet;
import org.microbean.scopelet.ScopedInstances;
import org.microbean.scopelet.Scopelet;
import org.microbean.scopelet.Scopes;
import org.microbean.scopelet.SingletonScopelet;

import static java.lang.constant.ConstantDescs.BSM_INVOKE;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.microbean.bean.Selectables.ambiguityReducing;
import static org.microbean.bean.Selectables.typesafeFiltering;

final class TestBBClientProxier {

  private Domain domain;

  private Qualifiers qualifiers;

  private Request<Void, Void> r;

  private Instances instances;

  private Bean<?> gorpBean;

  private TestBBClientProxier() {
    super();
  }

  @BeforeEach
  final void setup() {
    this.domain = new DefaultDomain();

    final BeanTypes beanTypes = new BeanTypes(domain);

    this.qualifiers = new Qualifiers();

    final Scopes scopes = new Scopes(qualifiers);

    // TODO: all this scopelet tunneling stuff is a specific instance of a general case that is already handle-able. If
    // you conceive of a Factory that can "create" (cache and return) all kinds of objects, and can use this tunneling
    // mechanism to look it up, then you don't need a Scopelet type.

    // Rule: your parent scope goes on the Any qualifier. All beans have the Any qualifier (normally) so this is a
    // convenient way to "tunnel" a qualifier/scope without screwing up typesafe resolution and without requiring
    // something stupid like BeanAttributes in CDI. You just add meta-annotations.
    final Attributes anyQualifierWithSingletonParentScope = Attributes.of("Any", qualifiers.qualifier(), scopes.singleton());

    final Bean<?> domainBean =
      new Bean<>(new Id(beanTypes.beanTypes(domain.declaredType(DefaultDomain.class.getCanonicalName())),
                        List.of(anyQualifierWithSingletonParentScope, qualifiers.defaultQualifier())),
                 c -> domain);

    final Bean<?> beanTypesBean =
      new Bean<>(new Id(beanTypes.beanTypes(domain.declaredType(BeanTypes.class.getCanonicalName())),
                        List.of(anyQualifierWithSingletonParentScope, qualifiers.defaultQualifier())),
                 c -> beanTypes);

    // TODO: we should do this for all the matchers, too, e.g. BeanTypeMatcher, BeanQualifiersMatcher,
    // EventTypesMatcher, etc. etc.
    //
    // BBClientProxier too
    //
    // Then all these handcrafted beans could say what they need as dependencies and assign them

    final Attributes application =
      Attributes.of("Application",
                    scopes.normal(),
                    Map.of(),
                    Map.of("Application",
                           List.of(qualifiers.qualifier(),
                                   scopes.scope(),
                                   scopes.singleton())));

    // Set up scopes.
    final Bean<?> applicationAndSingletonScopeletBean =
      new Bean<>(new Id(beanTypes.beanTypes(domain.declaredType(SingletonScopelet.class.getCanonicalName())),
                        // Rule: application and scopes.singleton() function here as qualifiers
                        List.of(anyQualifierWithSingletonParentScope, application, scopes.singleton())),
                 new SingletonScopelet());

    final Bean<?> noneScopeletBean =
      new Bean<>(new Id(beanTypes.beanTypes(domain.declaredType(NoneScopelet.class.getCanonicalName())),
                        // Rule: NONE_ID functions here as a qualifier
                        List.of(anyQualifierWithSingletonParentScope, scopes.none())),
                 new NoneScopelet());

    // Set up the user-supplied bean. Note that it is in application scope.
    this.gorpBean =
      new Bean<>(new Id(new BeanTypes(domain).beanTypes(domain.declaredType(Gorp.class.getCanonicalName())),
                        List.of(Attributes.of("Any", qualifiers.qualifier(), application), qualifiers.defaultQualifier())),
                 c -> new Gorp());

    // In a production version of all this we need to allow for supplying a preinitialized selection cache. We will
    // supply an empty one.
    final Map<AttributedType, List<Bean<?>>> selectionCache = new ConcurrentHashMap<>();

    // See https://jakarta.ee/specifications/cdi/4.1/jakarta-cdi-spec-4.1#unsatisfied_and_ambig_dependencies
    Selectable<AttributedType, Bean<?>> selectable =
      ScopedInstances.selectableOf(domain,
                                   Selectables.<AttributedType, Bean<?>>caching(ambiguityReducing(typesafeFiltering(List.of(domainBean,
                                                                                                                            beanTypesBean,
                                                                                                                            noneScopeletBean,
                                                                                                                            applicationAndSingletonScopeletBean,
                                                                                                                            this.gorpBean),
                                                                                                                    new IdMatcher(new BeanTypeMatcher(domain),
                                                                                                                                  new BeanQualifiersMatcher(qualifiers),
                                                                                                                                  new InterceptorBindingsMatcher())),
                                                                                                  org.microbean.bean.Ranked::alternate,
                                                                                                  org.microbean.bean.Ranked::rank
                                                                                                  ),
                                                                                selectionCache::computeIfAbsent));

    this.instances = new ScopedInstances(domain, qualifiers, scopes);
    this.r =
      new Request<Void, Void>(this.domain,
                              selectable,
                              this.instances,
                              new BBClientProxier(domain));
  }

  private static final boolean alternate(final Bean<?> b) {
    final Id id = b.id();
    return false;
  }

  @Test
  final void testGorpIsProxiable() {
    assertTrue(this.instances.proxiable(this.gorpBean.id()));
  }

  @Test
  final void testClientProxySunnyDay() {
    final Gorp g = this.r.<Gorp>reference(new AttributedType(this.domain.declaredType(Gorp.class.getCanonicalName()), qualifiers.defaultQualifiers()));
    assertTrue(g instanceof Proxy<?>, String.valueOf(g));
    assertTrue(g.getClass().isSynthetic());
    @SuppressWarnings("unchecked")
    final Gorp proxied = ((Proxy<Gorp>)g).$proxied();
    assertNotSame(g, proxied);
    assertSame(Gorp.class, proxied.getClass());
    assertEquals("bar", g.bar());
  }

  static interface Frobber {

    String frob();

  }

  static class Gorp implements Frobber {

    Gorp() {
      super();
    }

    @Override // Frobber
    public String frob() {
      return "frob";
    }

    String bar() {
      return "bar";
    }

  }

}
