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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.microbean.attributes.Attributes;

import org.microbean.bean.AttributedType;
import org.microbean.bean.Bean;
import org.microbean.bean.BeanTypeList;
import org.microbean.bean.BeanTypes;
import org.microbean.bean.Constant;
import org.microbean.bean.Id;
import org.microbean.bean.Request;
import org.microbean.bean.Selectable;
import org.microbean.bean.IdMatcher;
import org.microbean.bean.BeanTypeMatcher;
import org.microbean.bean.InterceptorBindingsMatcher;
import org.microbean.bean.BeanQualifiersMatcher;
import org.microbean.bean.Reducer;
import org.microbean.bean.RankedReducer;
import org.microbean.bean.Reducible;

import org.microbean.construct.DefaultDomain;
import org.microbean.construct.Domain;

import org.microbean.reference.ClientProxy;
import org.microbean.reference.DefaultRequest;

import org.microbean.scopelet.MapBackedScopelet;
import org.microbean.scopelet.NoneScopelet;
import org.microbean.scopelet.ScopedInstances;
import org.microbean.scopelet.Scopelet;
import org.microbean.scopelet.SingletonScopelet;

import static java.lang.constant.ConstantDescs.BSM_INVOKE;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.microbean.assign.Qualifiers.anyQualifier;
import static org.microbean.assign.Qualifiers.anyAndDefaultQualifiers;
import static org.microbean.assign.Qualifiers.defaultQualifier;
import static org.microbean.assign.Qualifiers.defaultQualifiers;
import static org.microbean.assign.Qualifiers.qualifier;

import static org.microbean.bean.Beans.cachingSelectableOf;

import static org.microbean.scopelet.Scopelet.APPLICATION_ID;
import static org.microbean.scopelet.Scopelet.NONE_ID;
import static org.microbean.scopelet.Scopelet.SCOPE;
import static org.microbean.scopelet.Scopelet.SINGLETON_ID;

final class TestBBClientProxier {

  private Domain domain;

  private Request<?> r;

  private TestBBClientProxier() {
    super();
  }

  @BeforeEach
  final void setup() {
    this.domain = new DefaultDomain();

    // Rule: your parent scope goes on the Any qualifier
    final Attributes anyQualifierWithSingletonParentScope = Attributes.of("Any", qualifier(), SINGLETON_ID);
    final BeanTypes beanTypes = new BeanTypes(domain);
    final Bean<?> applicationAndSingletonScopeletBean =
      new Bean<>(new Id(beanTypes.beanTypes(domain.declaredType(SingletonScopelet.class.getCanonicalName())),
                        // Rule: APPLICATION_ID and SINGLETON_ID function here as qualifiers
                        List.of(anyQualifierWithSingletonParentScope, APPLICATION_ID, SINGLETON_ID)),
                 new SingletonScopelet(domain));
    final Bean<?> noneScopeletBean =
      new Bean<>(new Id(beanTypes.beanTypes(domain.declaredType(NoneScopelet.class.getCanonicalName())),
                        // Rule: NONE_ID functions here as a qualifier
                        List.of(anyQualifierWithSingletonParentScope, NONE_ID)),
                 new NoneScopelet(domain));
    
    final Selectable<AttributedType, Bean<?>> selectable =
      cachingSelectableOf(List.of(noneScopeletBean,
                                  applicationAndSingletonScopeletBean,
                                  new Bean<>(new Id(new BeanTypes(domain).beanTypes(domain.declaredType(Gorp.class.getCanonicalName())),
                                                    List.of(Attributes.of("Any", qualifier(), APPLICATION_ID), defaultQualifier())),
                                             r -> new Gorp())),
                          new IdMatcher(new BeanQualifiersMatcher(),
                                        new InterceptorBindingsMatcher(),
                                        new BeanTypeMatcher(domain)),
                          Map.of());
    this.r =
      new DefaultRequest<Void>(selectable,
                               ScopedInstances.reducible(domain, selectable),
                               new ScopedInstances(domain),
                               new BBClientProxier(domain));
  }

  @Test
  final void testClientProxySunnyDay() {
    final Gorp g = this.r.reference(new AttributedType(this.domain.declaredType(Gorp.class.getCanonicalName()), defaultQualifiers()));
    assertTrue(g instanceof ClientProxy<?>, String.valueOf(g));
    assertTrue(g.getClass().isSynthetic());
    @SuppressWarnings("unchecked")
    final Gorp proxied = ((ClientProxy<Gorp>)g).$proxied();
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

    @Override
    public String frob() {
      return "frob";
    }

    String bar() {
      return "bar";
    }

  }

}
