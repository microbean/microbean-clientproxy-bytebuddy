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

import java.io.File;
import java.io.IOException;

import java.lang.invoke.MethodHandles;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.util.List;

import java.util.function.Supplier;

import net.bytebuddy.dynamic.DynamicType;

import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

import net.bytebuddy.pool.TypePool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.microbean.construct.DefaultDomain;
import org.microbean.construct.Domain;

import org.microbean.reference.ClientProxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TestBBClientProxyClassGenerator {

  private Domain domain;

  private TypePool typePool;

  private BBClientProxyClassGenerator g;

  TestBBClientProxyClassGenerator() {
    super();
  }

  @BeforeEach
  final void setup() {
    this.domain = new DefaultDomain();
    this.typePool = new TypeElementTypePool(this.domain);
    this.g = new BBClientProxyClassGenerator(this.typePool);
  }

  @Test
  final void testGenerateSunnyDay() throws IOException, IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException {
    final String proxyClassName = "org.microbean.clientproxy.bytebuddy.GorpProxy0";
    final Supplier<? extends Gorp> s = Gorp::new;
    final DynamicType.Loaded<?> dtl =
      this.g.generate(proxyClassName,
                      this.typePool.describe(Gorp.class.getCanonicalName()).resolve(),
                      List.of(this.typePool.describe(Cloneable.class.getCanonicalName()).resolve()))
      .load(this.getClass().getClassLoader(), ClassLoadingStrategy.UsingLookup.withFallback(MethodHandles::lookup));
    dtl.saveIn(new File(System.getProperty("project.build.testOutputDirectory")));
    final Class<?> cls = dtl.getLoaded();

    assertEquals(proxyClassName, cls.getName());
    assertTrue(ClientProxy.class.isAssignableFrom(cls));
    assertTrue(Gorp.class.isAssignableFrom(cls));
    assertTrue(Cloneable.class.isAssignableFrom(cls));

    final Constructor<?> c = cls.getDeclaredConstructor(Supplier.class);
    @SuppressWarnings("unchecked")
    final ClientProxy<Gorp> cp = (ClientProxy<Gorp>)c.newInstance(s);

    assertTrue(cp instanceof Gorp);
    assertTrue(cp instanceof Cloneable);
    assertSame(cls, cp.getClass());
    assertSame(cp, cp.$cast());

    final Gorp proxied = cp.$proxied();
    assertNotNull(proxied);
    assertNotSame(cp, proxied);
    assertNotSame(proxied, cp.$proxied()); // because of the Supplier implementation above

    final Gorp cast = cp.$cast();
    assertEquals("frob", cast.frob());
    assertNotEquals(cast, proxied);
    assertNotEquals(proxied, cast);

  }

  private static class Gorp {

    Gorp() {
      super();
    }

    String frob() {
      return "frob";
    }

  }

}
