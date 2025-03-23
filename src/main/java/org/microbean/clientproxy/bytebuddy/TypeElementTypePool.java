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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

import net.bytebuddy.ClassFileVersion;

import net.bytebuddy.description.annotation.AnnotationValue;

import net.bytebuddy.dynamic.ClassFileLocator;

import net.bytebuddy.pool.TypePool;

import org.microbean.construct.Domain;

import org.microbean.construct.vm.AccessFlags;
import org.microbean.construct.vm.Signatures;
import org.microbean.construct.vm.TypeDescriptors;

/**
 * A {@link TypePool.Default} that produces {@link net.bytebuddy.description.type.TypeDescription}s from {@code
 * javax.lang.model.*} constructs.
 *
 * <p>Notably, this approach does not cause classloading to occur.</p>
 *
 * @author <a href="https://about.me/lairdnelson/" target="_top">Laird Nelson</a>
 *
 * @see #doDescribe(String)
 */
public final class TypeElementTypePool extends TypePool.Default {


  /*
   * Instance fields.
   */


  private final ClassFileVersion classFileVersion;

  private final Domain domain;


  /*
   * Constructors.
   */


  /**
   * Creates a new {@link TypeElementTypePool}.
   *
   * @param domain a {@link Domain}; must not be {@code null}
   *
   * @exception NullPointerException if {@code domain} is {@code null}
   *
   * @see #TypeElementTypePool(ClassFileVersion, TypePool.CacheProvider, Domain)
   */
  public TypeElementTypePool(final Domain domain) {
    this(null, null, domain);
  }

  /**
   * Creates a new {@link TypeElementTypePool}.
   *
   * @param cacheProvider a {@link TypePool.CacheProvider}; may be {@code null} in which case a new {@link
   * TypePool.CacheProvider.Simple} will be used instead
   *
   * @param domain a {@link Domain}; must not be {@code null}
   *
   * @exception NullPointerException if {@code domain} is {@code null}
   *
   * @see #TypeElementTypePool(ClassFileVersion, TypePool.CacheProvider, Domain)
   */
  public TypeElementTypePool(final TypePool.CacheProvider cacheProvider, final Domain domain) {
    this(null, cacheProvider, domain);
  }

  /**
   * Creates a new {@link TypeElementTypePool}.
   *
   * @param classFileVersion a {@link ClassFileVersion}; may be {@code null} in which case the return value of an
   * invocation of {@link ClassFileVersion#ofThisVm()} will be used instead
   *
   * @param cacheProvider a {@link TypePool.CacheProvider}; may be {@code null} in which case a new {@link
   * TypePool.CacheProvider.Simple} will be used instead
   *
   * @param domain a {@link Domain}; must not be {@code null}
   *
   * @exception NullPointerException if {@code domain} is {@code null}
   */
  public TypeElementTypePool(final ClassFileVersion classFileVersion,
                             final TypePool.CacheProvider cacheProvider,
                             final Domain domain) {
    super(cacheProvider == null ? new TypePool.CacheProvider.Simple() : cacheProvider,
          ClassFileLocator.NoOp.INSTANCE, // no locator
          TypePool.Default.ReaderMode.FAST); // irrelevant; doesn't read class files
    this.domain = Objects.requireNonNull(domain, "domain");
    this.classFileVersion = classFileVersion == null ? ClassFileVersion.ofThisVm() : classFileVersion;
  }


  /*
   * Instance methods.
   */


  /**
   * Given a name of an only partially defined format that names a Java language array, primitive, or declared type, or
   * the {@code void} type, returns a {@link Resolution} describing a {@link TypeDescription} corresponding to that
   * name.
   *
   * <p>The {@linkplain #describe(String)} method, which this one overrides only for documentation purposes, does not
   * document what the supplied {@code name} must be beyond this:</p>
   *
   * <blockquote>The name of the type to describe. The name is to be written as when calling {@link Class#getName()
   * Class.getName()}.</blockquote>
   *
   * <p>We call names of this sort <dfn>type pool names</dfn>.</p>
   *
   * @param typePoolName a <dfn>type pool name</dfn> whose validity requirements are expressed only in <a
   * href="https://github.com/raphw/byte-buddy/blob/byte-buddy-1.16.0/byte-buddy-dep/src/main/java/net/bytebuddy/pool/TypePool.java#L576-L602">source
   * code</a>; must not be {@code null}
   *
   * @return a non-{@code null} {@link Resolution}
   *
   * @exception NullPointerException if {@code typePoolName} is {@code null}
   *
   * @see TypePool#describe(String)
   *
   * @see Class#forName(String)
   *
   * @see Class#getName()
   *
   * @see Domain#typeElement(CharSequence)
   *
   * @see <a
   * href="https://github.com/raphw/byte-buddy/blob/byte-buddy-1.16.0/byte-buddy-dep/src/main/java/net/bytebuddy/pool/TypePool.java#L576-L602">Source
   * code for the <code>TypePool.AbstractBase</code> class</a>
   *
   * @see <a href="https://github.com/raphw/byte-buddy/issues/1754">Byte Buddy issue 1754</a>
   *
   * @spec https://docs.oracle.com/javase/specs/jls/se23/html/jls-6.html#jls-6.7 Java Language Specification, section
   * 6.7
   *
   * @spec https://docs.oracle.com/javase/specs/jls/se23/html/jls-13.html#jls-13.1 Java Language Specification, section
   * 13.1
   *
   * @spec https://docs.oracle.com/javase/specs/jvms/se23/html/jvms-4.html#jvms-4.7.9.1 Java Virtual Machine
   * Specification, section 4.7.9.1
   */
  @Override // TypePool.Default
  public final Resolution describe(final String typePoolName) {
    // https://github.com/raphw/byte-buddy/blob/byte-buddy-1.16.0/byte-buddy-dep/src/main/java/net/bytebuddy/pool/TypePool.java#L576-L602
    return super.describe(typePoolName);
  }

  @Override // TypePool.Default
  protected final Resolution doDescribe(final String binaryName) {
    // Note that we deliberately and I believe properly do not use the two-argument form of
    // domain#typeElement(ModuleElement, CharSequence), since inferring the ModuleElement representing the caller here
    // is all but impossible (except for StackWalker).
    
    // If binaryName is the binary name of a top-level class, this will work just fine because a top level class' binary
    // and canonical names are the same, and canonical names are what domain#typeElement(CharSequence) accepts.
    TypeElement e = this.domain.typeElement(binaryName);
    if (e == null) {
      // If binaryName is not the binary name of a top-level class, then by the time we're in this method it is the
      // binary name of an inner or nested class, and there is no perfectly reliable way to convert such a binary name
      // into a canonical name (or, for that matter, to go the other way). For *most* cases, changing '$' to '.' to
      // yield a valid canonical name for an inner or nested class is good enough, so that's what we do here.
      e = this.domain.typeElement(binaryName.replace('$', '.'));
    }
    return
      e == null ?
      new Resolution.Illegal(binaryName + "; " + binaryName.replace('%', '.')) :
      new Resolution.Simple(new TypeDescription(this.domain, e));
  }


  /*
   * Inner and nested classes.
   */


  private final class TypeDescription extends LazyTypeDescription {


    /*
     * Static fields.
     */


    private static final String[] EMPTY_STRING_ARRAY = new String[0];


    /*
     * Constructors.
     */


    // Binary names in this mess are JVM binary names, not JLS binary names. Raph calls them "internal names" which
    // isn't a thing. He's probably referring to the paragraph in the Java Virtual Machine Specification section 4.3.1
    // that reads: "For historical reasons, the syntax of binary names that appear in class file structures differs from
    // the syntax of binary names documented in JLS §13.1. In this *internal* [emphasis mine] form, the ASCII periods
    // (.) that normally separate the identifiers which make up the binary name are replaced by ASCII forward slashes
    // (/). The identifiers themselves must be unqualified names (§4.2.2)." On the other hand, that is not the kind of
    // binary name returned by, for example, javax.lang.model.util.Elements#getBinaryName(TypeElement). So maybe ASM
    // does the conversion from "real" binary name to "internal" binary name and that's what he's talking about?
    //
    // The consumer of these sorts of names appears to be
    // https://asm.ow2.io/javadoc/org/objectweb/asm/signature/SignatureVisitor.html.
    //
    // Raph's javadoc for NamedElement#getInternalName() reads, in part: "The internal name of this byte code element as
    // used within the Java class file format", which suggests we should look at the JVM specification for answers. See,
    // for example, https://docs.oracle.com/javase/specs/jvms/se23/html/jvms-4.html#jvms-4.7.9.1. Maybe those are the
    // sorts of things meant by "internal name"?
    //
    // See also https://docs.oracle.com/javase/specs/jvms/se23/html/jvms-4.html#jvms-4.2.1.
    /**
     * Creates a new {@link TypeDescription}.
     *
     * @param domain a {@link Domain}; must not be {@code null}
     *
     * @param e a {@link TypeElement}; must not be {@code null}
     *
     * @exception NullPointerException if either argument is {@code null}
     *
     * @spec https://docs.oracle.com/javase/specs/jvms/se23/html/jvms-4.html#jvms-4.2.1 Java Virtual Machine
     * Specification, section 4.2.1
     */
    private TypeDescription(final Domain domain, final TypeElement e) {
      super(TypeElementTypePool.this,
            actualModifiers(domain, e),
            modifiers(domain, e),
            domain.toString(domain.binaryName(e)), // "internalName"
            binaryName(domain, e.getSuperclass()), // "superClassName"
            interfaceBinaryNames(domain, e), // "interfaceName" (yes, singular for some reason)
            genericSignature(domain, e), // "genericSignature"; ASM just calls it a "signature" and seems to be expecting a *type* signature in the JVM parlance
            typeContainment(domain, e),
            declaringTypeBinaryName(domain, e),
            declaredTypeDescriptors(domain, e),
            e.getNestingKind() == NestingKind.ANONYMOUS,
            nestHostBinaryName(e),
            nestMemberBinaryNames(e),
            superclassAnnotationTokens(e),
            interfaceAnnotationTokens(e),
            typeVariableAnnotationTokens(e),
            typeVariableBoundsAnnotationTokens(e),
            annotationTokens(e),
            fieldTokens(domain, e),
            methodTokens(domain, e),
            recordComponentTokens(domain, e),
            permittedSubclassBinaryNames(domain, e),
            classFileVersion);
    }


    /*
     * Static methods.
     */


    private static final String binaryName(final Domain domain, final TypeMirror t) {
      final Element e = domain.element(t);
      return e instanceof TypeElement te ? domain.toString(domain.binaryName(te)) : null;
    }

    private static final int actualModifiers(final Domain domain, final Element e) {
      return AccessFlags.accessFlags(e, domain);
    }

    private static final int modifiers(final Domain domain, final Element e) {
      return AccessFlags.accessFlags(e, domain);
    }

    private static final String[] interfaceBinaryNames(final Domain domain, final TypeElement e) {
      final List<? extends TypeMirror> ifaces = e.getInterfaces();
      if (ifaces.isEmpty()) {
        return EMPTY_STRING_ARRAY;
      }
      final String[] rv = new String[ifaces.size()];
      for (int i = 0; i < rv.length; i++) {
        rv[i] = binaryName(domain, ifaces.get(i));
      }
      return rv;
    }

    private static final String genericSignature(final Domain domain, final Element e) {
      return Signatures.signature(e, domain);
      // return domain.elementSignature(e);
    }

    private static final TypeContainment typeContainment(final Domain domain, final Element e) {
      final TypeElement ee = (TypeElement)e.getEnclosingElement();
      if (ee == null) {
        return TypeContainment.SelfContained.INSTANCE;
      }
      return switch (ee.getKind()) {
      case METHOD ->
        new TypeContainment.WithinMethod(domain.toString(domain.binaryName((TypeElement)ee.getEnclosingElement())),
                                         domain.toString(ee.getSimpleName()), // TODO: maybe? needs to be method's "internal name" which is just its "unqualified name" (4.2.2 JVM)
                                         TypeDescriptors.typeDescriptor(ee.asType(), domain).descriptorString()) {};
      case ANNOTATION_TYPE, CLASS, ENUM, INTERFACE, RECORD ->
        new TypeContainment.WithinType(domain.toString(domain.binaryName(ee)),
                                       ee.getNestingKind() == NestingKind.LOCAL) {}; // TODO: this is for the enclosing element, yes?
      case PACKAGE -> TypeContainment.SelfContained.INSTANCE;
      default -> throw new IllegalStateException(); // I guess?
      };
    }

    private static final String declaringTypeBinaryName(final Domain domain, final TypeElement e) {
      // TODO: triple check: getEnclosingType()? or getEnclosingElement.asType()?
      final TypeMirror t = ((DeclaredType)e.asType()).getEnclosingType();
      if (t == null || t.getKind() == TypeKind.NONE) {
        return null;
      }
      return domain.toString(domain.binaryName((TypeElement)((DeclaredType)t).asElement()));
    }

    private static final List<String> declaredTypeDescriptors(final Domain domain, final Element e) {
      final ArrayList<String> l = new ArrayList<>();
      for (final Element ee : e.getEnclosedElements()) {
        if (ee.getKind().isDeclaredType()) {
          l.add(TypeDescriptors.typeDescriptor(ee.asType(), domain).descriptorString());
        }
      }
      l.trimToSize();
      return Collections.unmodifiableList(l);
    }

    private static final String nestHostBinaryName(final Element e) {
      return null;
    }

    private static final List<String> nestMemberBinaryNames(final Element e) {
      return List.of();
    }

    private static final Map<String, List<AnnotationToken>> superclassAnnotationTokens(final Element e) {
      return Map.of();
    }

    private static final Map<Integer, Map<String, List<AnnotationToken>>> interfaceAnnotationTokens(final Element e) {
      return Map.of();
    }

    private static final Map<Integer, Map<String, List<AnnotationToken>>> typeVariableAnnotationTokens(final Element e) {
      return Map.of();
    }

    private static final Map<Integer, Map<Integer, Map<String, List<AnnotationToken>>>> typeVariableBoundsAnnotationTokens(final Element e) {
      return Map.of();
    }

    private static final List<AnnotationToken> annotationTokens(final Element e) {
      return List.of();
    }

    private static final List<FieldToken> fieldTokens(final Domain domain, final Element e) {
      final ArrayList<FieldToken> l = new ArrayList<>();
      for (final Element ee : e.getEnclosedElements()) {
        if (ee.getKind().isField()) {
          l.add(fieldToken(domain, (VariableElement)ee));
        }
      }
      l.trimToSize();
      return Collections.unmodifiableList(l);
    }

    private static final List<MethodToken> methodTokens(final Domain domain, final Element e) {
      final ArrayList<MethodToken> l = new ArrayList<>();
      for (final Element ee : e.getEnclosedElements()) {
        if (ee.getKind().isExecutable()) {
          l.add(methodToken(domain, (ExecutableElement)ee));
        }
      }
      l.trimToSize();
      return Collections.unmodifiableList(l);
    }

    private static final List<RecordComponentToken> recordComponentTokens(final Domain domain, final Element e) {
      final ArrayList<RecordComponentToken> l = new ArrayList<>();
      for (final Element ee : e.getEnclosedElements()) {
        if (ee.getKind() == ElementKind.RECORD_COMPONENT) {
          l.add(recordComponentToken(domain, (RecordComponentElement)ee));
        }
      }
      l.trimToSize();
      return Collections.unmodifiableList(l);
    }

    private static final List<String> permittedSubclassBinaryNames(final Domain domain, final TypeElement e) {
      final List<? extends TypeMirror> ts = e.getPermittedSubclasses();
      if (ts.isEmpty()) {
        return List.of();
      }
      final List<String> l = new ArrayList<>(ts.size());
      for (final TypeMirror t : ts) {
        l.add(domain.toString(domain.binaryName((TypeElement)((DeclaredType)t).asElement())));
      }
      return Collections.unmodifiableList(l);
    }

    private static final FieldToken fieldToken(final Domain domain, final VariableElement e) {
      if (!e.getKind().isField()) {
        throw new IllegalArgumentException("e: " + e);
      }
      return
        new FieldToken(domain.toString(e.getSimpleName()),
                       AccessFlags.accessFlags(e, domain),
                       TypeDescriptors.typeDescriptor(e.asType(), domain).descriptorString(),
                       genericSignature(domain, e),
                       Map.of(), // TODO: typeAnnotationTokens
                       List.of()) {}; // TODO: annotationTokens
    }

    private static final MethodToken methodToken(final Domain domain, final ExecutableElement e) {
      final List<? extends TypeMirror> thrownTypes = e.getThrownTypes();
      final String[] exceptionBinaryNames;
      if (thrownTypes.isEmpty()) {
        exceptionBinaryNames = EMPTY_STRING_ARRAY;
      } else {
        exceptionBinaryNames = new String[thrownTypes.size()];
        for (int i = 0; i < exceptionBinaryNames.length; i++) {
          exceptionBinaryNames[i] = domain.toString(domain.binaryName((TypeElement)((DeclaredType)thrownTypes.get(i)).asElement()));
        }
      }
      final ArrayList<MethodTokenSubclass.ParameterTokenSubclass> parameterTokens = new ArrayList<>();
      for (final VariableElement p : e.getParameters()) {
        parameterTokens.add(parameterToken(domain, p));
      }
      parameterTokens.trimToSize();
      return
        new MethodTokenSubclass(domain.toString(e.getSimpleName()),
                                AccessFlags.accessFlags(e, domain),
                                TypeDescriptors.typeDescriptor(e.asType(), domain).descriptorString(),
                                genericSignature(domain, e),
                                exceptionBinaryNames,
                                typeVariableAnnotationTokens(e),
                                typeVariableBoundsAnnotationTokens(e),
                                Map.of(), // returnTypeAnnotationTokens
                                Map.of(), // parameterTypeAnnotationTokens
                                Map.of(), // exceptionTypeAnnotationTokens
                                Map.of(), // receiverTypeAnnotationTokens
                                annotationTokens(e),
                                Map.of(), // parameterAnnotationTokens
                                Collections.unmodifiableList(parameterTokens),
                                null); // defaultValue
    }

    private static final MethodTokenSubclass.ParameterTokenSubclass parameterToken(final Domain domain, final VariableElement e) {
      final int accessFlags = AccessFlags.accessFlags(e, domain);
      return new MethodTokenSubclass.ParameterTokenSubclass(domain.toString(e.getSimpleName()), accessFlags == 0 ? null : Integer.valueOf(accessFlags));
    }

    private static final RecordComponentToken recordComponentToken(final Domain domain, final RecordComponentElement e) {
      return
        new RecordComponentToken(domain.toString(e.getSimpleName()),
                                 TypeDescriptors.typeDescriptor(e.asType(), domain).descriptorString(),
                                 genericSignature(domain, e),
                                 Map.of(),
                                 List.of()) {}; // annotationTokens
    }


    /*
     * Inner and nested classes.
     */


    private static final class MethodTokenSubclass extends MethodToken {


      /*
       * Constructors.
       */


      @SuppressWarnings("unchecked")
      private MethodTokenSubclass(final String name,
                                  final int modifiers,
                                  final String descriptor,
                                  final String genericSignature,
                                  final String[] exceptionName,
                                  final Map<Integer, Map<String, List<AnnotationToken>>> typeVariableAnnotationTokens,
                                  final Map<Integer, Map<Integer, Map<String, List<AnnotationToken>>>> typeVariableBoundAnnotationTokens,
                                  final Map<String, List<AnnotationToken>> returnTypeAnnotationTokens,
                                  final Map<Integer, Map<String, List<AnnotationToken>>> parameterTypeAnnotationTokens,
                                  final Map<Integer, Map<String, List<AnnotationToken>>> exceptionTypeAnnotationTokens,
                                  final Map<String, List<AnnotationToken>> receiverTypeAnnotationTokens,
                                  final List<AnnotationToken> annotationTokens,
                                  final Map<Integer, List<AnnotationToken>> parameterAnnotationTokens,
                                  final List<? extends ParameterToken> parameterTokens,
                                  final AnnotationValue<?,?> defaultValue) {
        super(name,
              modifiers,
              descriptor,
              genericSignature,
              exceptionName,
              typeVariableAnnotationTokens,
              typeVariableBoundAnnotationTokens,
              returnTypeAnnotationTokens,
              parameterTypeAnnotationTokens,
              exceptionTypeAnnotationTokens,
              receiverTypeAnnotationTokens,
              annotationTokens,
              parameterAnnotationTokens,
              (List<ParameterToken>)parameterTokens,
              defaultValue);
      }


      /*
       * Inner and nested classes.
       */


      private static final class ParameterTokenSubclass extends ParameterToken {


        /*
         * Constructors.
         */


        private ParameterTokenSubclass(final String name, final Integer modifiers) {
          super(name, modifiers);
        }

      }

    }

  }

}
