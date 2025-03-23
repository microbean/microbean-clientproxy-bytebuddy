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

import net.bytebuddy.pool.TypePool;

import net.bytebuddy.description.type.TypeDescription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.microbean.construct.DefaultDomain;
import org.microbean.construct.Domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class TestTypeElementTypePool {

  private TypePool tp;

  private Domain domain;

  private TestTypeElementTypePool() {
    super();
  }

  @BeforeEach
  final void setup() {
    this.domain = new DefaultDomain();
    this.tp = new TypeElementTypePool(new TypePool.CacheProvider.Simple(), this.domain);
  }

  @Test
  final void testFirstSpike() {
    final TypeDescription td = tp.describe("java.lang.Integer").resolve();
    assertEquals("java.lang.Integer", td.getCanonicalName());
  }

  @Test
  final void testPrimitive() {
    final TypeDescription td = tp.describe("int").resolve();
    assertEquals("int", td.getCanonicalName());
  }

  // https://github.com/raphw/byte-buddy/blob/byte-buddy-1.16.0/byte-buddy-dep/src/main/java/net/bytebuddy/pool/TypePool.java#L583-L587
  @Test
  final void testArrayInput() {
    final TypeDescription td = tp.describe("[I").resolve();
    assertEquals("int[]", td.getCanonicalName());
    assertEquals("int[]", int[].class.getCanonicalName());
  }

}
