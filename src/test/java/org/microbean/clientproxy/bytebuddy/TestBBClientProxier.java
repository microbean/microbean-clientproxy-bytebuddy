/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2025–2026 microBean™.
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
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import javax.lang.model.AnnotatedConstruct;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.VariableElement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.microbean.assign.Annotated;
import org.microbean.assign.Selectable;
import org.microbean.assign.Selectables;

import org.microbean.bean.Bean;
import org.microbean.bean.BeanQualifiersMatcher;
import org.microbean.bean.BeanTypeMatcher;
import org.microbean.bean.BeanTypes;
import org.microbean.bean.Beans;
import org.microbean.bean.Id;
import org.microbean.bean.IdMatcher;
import org.microbean.bean.Qualifiers;

import org.microbean.construct.DefaultDomain;
import org.microbean.construct.Domain;

import org.microbean.construct.element.SyntheticAnnotationMirror;
import org.microbean.construct.element.SyntheticAnnotationTypeElement;
import org.microbean.construct.element.SyntheticAnnotationValue;
import org.microbean.construct.element.SyntheticLocalVariableElement;

import org.microbean.producer.InterceptorBindings;
import org.microbean.producer.InterceptorBindingsMatcher;

import org.microbean.proxy.Proxy;

import org.microbean.reference.Request;

import org.microbean.scopelet.NoneScopelet;
import org.microbean.scopelet.ScopedInstances;
import org.microbean.scopelet.Scopes;
import org.microbean.scopelet.SingletonScopelet;

import static javax.lang.model.element.ElementKind.ENUM_CONSTANT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.microbean.bean.Selectables.ambiguityReducing;

final class TestBBClientProxier {

  private Domain domain;

  private Qualifiers bq;

  private Request<Void, Void> r;

  private ScopedInstances scopedInstances;

  private Bean<?> gorpBean;

  private TestBBClientProxier() {
    super();
  }

  @BeforeEach
  final void setup() {
    final Domain domain = new DefaultDomain();
    final BeanTypes beanTypes = new BeanTypes(domain);
    final org.microbean.assign.Qualifiers aq = new org.microbean.assign.Qualifiers(domain);
    final Qualifiers bq = new Qualifiers(domain, aq); // TODO: add predicate representing @Nonbinding
    final org.microbean.scopelet.Qualifiers sq = new org.microbean.scopelet.Qualifiers(domain, aq);
    final Scopes scopes = new Scopes(domain, aq, sq);
    final AnnotationMirror anyQualifier = bq.anyQualifier();
    final AnnotationMirror defaultQualifier = bq.defaultQualifier();

    // Set up the singleton scope annotation.
    final SyntheticAnnotationMirror anyQualifierWithSingletonParentScope = new SyntheticAnnotationMirror(anyQualifier);
    SyntheticAnnotationTypeElement.class.cast(anyQualifierWithSingletonParentScope.getAnnotationType().asElement())
      .getAnnotationMirrors()
      .add(scopes.singletonScope());

    // TODO: all this scopelet tunneling stuff is a specific instance of a general case that is already handle-able. If
    // you conceive of a Factory that can "create" (cache and return) all kinds of objects, and can use this tunneling
    // mechanism to look it up, then you don't need a Scopelet type.

    // Rule: your parent scope goes on the Any qualifier. All beans have the Any qualifier (normally) so this is a
    // convenient way to "tunnel" a qualifier/scope without screwing up typesafe resolution and without requiring
    // something stupid like BeanAttributes in CDI. You just add meta-annotations.

    final Bean<?> domainBean =
      new Bean<>(new Id(beanTypes.beanTypes(domain.declaredType(DefaultDomain.class.getCanonicalName())),
                        List.of(anyQualifierWithSingletonParentScope, defaultQualifier)),
                 c -> domain);

    final Bean<?> beanTypesBean =
      new Bean<>(new Id(beanTypes.beanTypes(domain.declaredType(BeanTypes.class.getCanonicalName())),
                        List.of(anyQualifierWithSingletonParentScope, defaultQualifier)),
                 c -> beanTypes);

    // TODO: other system level beans

    // Set up the application scope. To do this we need to do what Scopes does for other scopes.
    final List<? extends AnnotationMirror> as = domain.typeElement("java.lang.annotation.Documented").getAnnotationMirrors();
    assert as.size() == 3; // @Documented, @Retention, @Target, in that order, all annotated in turn with each other
    final AnnotationMirror documentedAnnotation = as.get(0);
    final AnnotationMirror retentionAnnotation = as.get(1);
    final List<AnnotationMirror> documentedRetentionTarget =
      List.of(documentedAnnotation, // @Documented
              retentionAnnotation, // @Retention(TargetType.RUNTIME) (happens fortuitously to be RUNTIME)
              as.get(2)); // @Target(ANNOTATION_TYPE)
    final List<SyntheticAnnotationValue> savs = new ArrayList<>(4);
    for (final Element e : domain.typeElement("java.lang.annotation.ElementType").getEnclosedElements()) {
      if (e.getKind() == ENUM_CONSTANT && e instanceof VariableElement ve) {
        final Name n = e.getSimpleName();
        if (n.contentEquals("TYPE") || n.contentEquals("METHOD") || n.contentEquals("FIELD")) {
          savs.add(new SyntheticAnnotationValue(ve));
        }
      }
    }
    final SyntheticAnnotationMirror applicationScope =
      new SyntheticAnnotationMirror(new SyntheticAnnotationTypeElement(List.of(documentedAnnotation,
                                                                               retentionAnnotation,
                                                                               new SyntheticAnnotationMirror(domain.typeElement("java.lang.annotation.Target"),
                                                                                                             Map.of("value", savs)),
                                                                               scopes.metaNormalScope(),
                                                                               aq.metaQualifier(),
                                                                               scopes.singletonScope()), // parent scope meta-annotation
                                                                       "Application"));
    final SyntheticAnnotationMirror anyQualifierWithApplicationParentScope = new SyntheticAnnotationMirror(anyQualifier);
    SyntheticAnnotationTypeElement.class.cast(anyQualifierWithApplicationParentScope.getAnnotationType().asElement())
      .getAnnotationMirrors()
      .add(applicationScope);

    // Set up scopelets.
    final Bean<?> applicationAndSingletonScopeletBean =
      new Bean<>(new Id(beanTypes.beanTypes(domain.declaredType(SingletonScopelet.class.getCanonicalName())),
                        // Rule: application and scopes.singletonScope() function here as qualifiers
                        List.of(anyQualifierWithSingletonParentScope, applicationScope, scopes.singletonScope())),
                 new SingletonScopelet());

    final Bean<?> noneScopeletBean =
      new Bean<>(new Id(beanTypes.beanTypes(domain.declaredType(NoneScopelet.class.getCanonicalName())),
                        // Rule: scopes.none() functions here as a qualifier
                        List.of(anyQualifierWithSingletonParentScope, scopes.noneScope())),
                 new NoneScopelet());

    // Set up the user-supplied bean. Note that it is in application scope.
    final Bean<?> gorpBean =
      new Bean<>(new Id(new BeanTypes(domain).beanTypes(domain.declaredType(Gorp.class.getCanonicalName())),
                        List.of(anyQualifierWithApplicationParentScope, defaultQualifier)),
                 c -> new Gorp());

    // In a production version of all this we need to allow for supplying a preinitialized selection cache. We will
    // supply an empty one.
    final Map<Annotated<? extends AnnotatedConstruct>, List<Bean<?>>> selectionCache = new ConcurrentHashMap<>();

    final ScopedInstances scopedInstances = new ScopedInstances(domain, bq, sq, scopes, null);
    // See https://jakarta.ee/specifications/cdi/4.1/jakarta-cdi-spec-4.1#unsatisfied_and_ambig_dependencies
    final Beans beans = new Beans(domain);
    Selectable<Annotated<? extends AnnotatedConstruct>, Bean<?>> selectable =
      scopedInstances.selectableOf(Selectables.<Annotated<? extends AnnotatedConstruct>, Bean<?>>caching(ambiguityReducing(beans.typesafeFilteringSelectable(List.of(domainBean,
                                                                                                                                                                     beanTypesBean,
                                                                                                                                                                     noneScopeletBean,
                                                                                                                                                                     applicationAndSingletonScopeletBean,
                                                                                                                                                                     gorpBean),
                                                                                                                                                             new IdMatcher(new BeanTypeMatcher(domain),
                                                                                                                                                                           new BeanQualifiersMatcher(aq, bq),
                                                                                                                                                                           new InterceptorBindingsMatcher(new InterceptorBindings(domain)))),
                                                                                                                           beans::alternate,
                                                                                                                           beans::rank),
                                                                                                         selectionCache::computeIfAbsent));

    final Request<Void, Void> r = new Request<>(domain, selectable, scopedInstances, new BBClientProxier(domain));

    this.domain = domain;
    this.bq = bq;
    this.scopedInstances = scopedInstances;
    this.gorpBean = gorpBean;
    this.r = r;
  }

  private static final boolean alternate(final Bean<?> b) {
    final Id id = b.id();
    return false;
  }

  @Test
  final void testGorpIsProxiable() {
    assertTrue(this.scopedInstances.proxiable(this.gorpBean.id()));
  }

  @Test
  final void testClientProxySunnyDay() {
    final AnnotatedConstruct ac = new SyntheticLocalVariableElement(this.bq.defaultQualifiers(), this.domain.declaredType(Gorp.class.getCanonicalName()));
    final Gorp g = this.r.<Gorp>reference(Annotated.of(ac));
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
