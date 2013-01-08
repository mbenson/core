/*
 * Copyright 2012-2013 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.jboss.forge.parser.java.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.jboss.forge.parser.java.Annotation;
import org.jboss.forge.parser.java.AnnotationTarget;
import org.jboss.forge.parser.java.JavaSource;
import org.jboss.forge.parser.java.impl.AnnotationImpl;
import org.jboss.forge.parser.java.util.Types;

/**
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
public class AnnotationAccessor<O extends JavaSource<O>, T>
{
   private enum Replace
   {

      BODY()
      {
         @SuppressWarnings("unchecked")
         @Override
         List<IExtendedModifier> modifiers(ASTNode parent)
         {
            return ((BodyDeclaration) parent).modifiers();
         }
      },
      VARIABLE()
      {
         @SuppressWarnings("unchecked")
         @Override
         List<IExtendedModifier> modifiers(ASTNode parent)
         {
            return ((SingleVariableDeclaration) parent).modifiers();
         }
      };
      abstract List<IExtendedModifier> modifiers(ASTNode parent);

      void replace(org.eclipse.jdt.core.dom.Annotation oldNode, org.eclipse.jdt.core.dom.Annotation newNode)
      {
         List<IExtendedModifier> modifiers = modifiers(oldNode.getParent());

         int pos = modifiers.indexOf(oldNode);
         if (pos >= 0)
         {
            modifiers.set(pos, newNode);
         }
      }
   }

   private class ConcreteAnnotation extends AnnotationImpl<O, T>
   {
      final Replace replace;

      ConcreteAnnotation(AnnotationTarget<O, T> parent, Object internal, Replace replace)
      {
         super(parent, internal);
         this.replace = replace;
      }

      ConcreteAnnotation(AnnotationTarget<O, T> parent, Replace replace)
      {
         super(parent);
         this.replace = replace;
      }

      @Override
      protected void replace(org.eclipse.jdt.core.dom.Annotation oldNode, org.eclipse.jdt.core.dom.Annotation newNode)
      {
         replace.replace(oldNode, newNode);
      }
   }

   private class AnnotationFactory
   {
      final AnnotationTarget<O, T> target;
      final Replace replace;

      AnnotationFactory(AnnotationTarget<O, T> target, Replace replace)
      {
         super();
         this.target = target;
         this.replace = replace;
      }

      Annotation<O> createAnnotationFor(org.eclipse.jdt.core.dom.Annotation node)
      {
         return new ConcreteAnnotation(target, node, replace);
      }
   }

   public Annotation<O> addAnnotation(final AnnotationTarget<O, T> target, final BodyDeclaration body)
   {
      return addTo(body.modifiers(), new ConcreteAnnotation(target, Replace.BODY));
   }

   public Annotation<O> addAnnotation(final AnnotationTarget<O, T> target,
            final SingleVariableDeclaration variableDeclaration)
   {
      return addTo(variableDeclaration.modifiers(), new ConcreteAnnotation(target, Replace.VARIABLE));
   }

   private Annotation<O> addTo(List<?> modifiers, final Annotation<O> result)
   {
      @SuppressWarnings("unchecked")
      ListIterator<IExtendedModifier> iter = (ListIterator<IExtendedModifier>) modifiers.listIterator();
      while (iter.hasNext() && iter.next().isAnnotation())
         ;

      // the effect of this is to back up only if the last encountered modifier is _not_ an annotation:
      if (iter.hasPrevious() && iter.previous().isAnnotation())
      {
         iter.next();
      }
      iter.add((org.eclipse.jdt.core.dom.Annotation) result.getInternal());
      return result;
   }

   public Annotation<O> addAnnotation(final AnnotationTarget<O, T> target, final BodyDeclaration body,
            final Class<?> clazz)
   {
      return addAnnotation(target, body, clazz.getName());
   }

   public Annotation<O> addAnnotation(final AnnotationTarget<O, T> target,
            final SingleVariableDeclaration variableDeclaration,
            final Class<?> clazz)
   {
      return addAnnotation(target, variableDeclaration, clazz.getName());
   }

   public Annotation<O> addAnnotation(final AnnotationTarget<O, T> target, final BodyDeclaration body,
            final String className)
   {
      if (Types.isQualified(className))
      {
         target.getOrigin().addImport(className);
      }
      return addTo(body.modifiers(),
               new ConcreteAnnotation(target, Replace.BODY).setName(Types.toSimpleName(className)));
   }

   public Annotation<O> addAnnotation(final AnnotationTarget<O, T> target,
            final SingleVariableDeclaration variableDeclaration,
            final String className)
   {
      if (Types.isQualified(className))
      {
         target.getOrigin().addImport(className);
      }
      return addTo(variableDeclaration.modifiers(),
               new ConcreteAnnotation(target, Replace.VARIABLE).setName(Types.toSimpleName(className)));
   }

   public List<Annotation<O>> getAnnotations(final AnnotationTarget<O, T> target, final BodyDeclaration body)
   {
      return getAnnotations(target, body.modifiers(), new AnnotationFactory(target, Replace.BODY));
   }

   public List<Annotation<O>> getAnnotations(final AnnotationTarget<O, T> target,
            final SingleVariableDeclaration variableDeclaration)
   {
      return getAnnotations(target, variableDeclaration.modifiers(), new AnnotationFactory(target, Replace.VARIABLE));
   }

   private List<Annotation<O>> getAnnotations(final AnnotationTarget<O, T> target, final List<?> modifiers,
            AnnotationFactory annotationFactory)
   {
      List<Annotation<O>> result = new ArrayList<Annotation<O>>();

      for (Object object : modifiers)
      {
         if (object instanceof org.eclipse.jdt.core.dom.Annotation)
         {
            result.add(annotationFactory.createAnnotationFor((org.eclipse.jdt.core.dom.Annotation) object));
         }
      }

      return Collections.unmodifiableList(result);
   }

   public <E extends AnnotationTarget<O, T>> E removeAnnotation(final E target, final BodyDeclaration body,
            final Annotation<O> annotation)
   {
      return removeAnnotation(target, body.modifiers(), annotation);
   }

   public <E extends AnnotationTarget<O, T>> E removeAnnotation(final E target,
            final SingleVariableDeclaration variableDeclaration,
            final Annotation<O> annotation)
   {
      return removeAnnotation(target, variableDeclaration.modifiers(), annotation);
   }

   private <E extends AnnotationTarget<O, T>> E removeAnnotation(final E target, final List<?> modifiers,
            final Annotation<O> annotation)
   {
      for (Object object : modifiers)
      {
         if (object.equals(annotation.getInternal()))
         {
            modifiers.remove(object);
            break;
         }
      }
      return target;
   }

   public <E extends AnnotationTarget<O, T>> boolean hasAnnotation(final E target, final BodyDeclaration body,
            final String type)
   {
      return getAnnotation(target, body, type) != null;
   }

   public <E extends AnnotationTarget<O, T>> boolean hasAnnotation(final E target,
            final SingleVariableDeclaration variableDeclaration,
            final String type)
   {
      return getAnnotation(target, variableDeclaration, type) != null;
   }

   public Annotation<O> getAnnotation(final AnnotationTarget<O, T> target, final BodyDeclaration body,
            final Class<? extends java.lang.annotation.Annotation> type)
   {
      return getAnnotation(target, body, type.getName());
   }

   public Annotation<O> getAnnotation(final AnnotationTarget<O, T> target,
            final SingleVariableDeclaration variableDeclaration,
            final Class<? extends java.lang.annotation.Annotation> type)
   {
      return getAnnotation(target, variableDeclaration, type.getName());
   }

   public Annotation<O> getAnnotation(final AnnotationTarget<O, T> target, final BodyDeclaration body, final String type)
   {
      return getFrom(body.modifiers(), new AnnotationFactory(target, Replace.BODY), type);
   }

   public Annotation<O> getAnnotation(final AnnotationTarget<O, T> target,
            final SingleVariableDeclaration variableDeclaration, final String type)
   {
      return getFrom(variableDeclaration.modifiers(), new AnnotationFactory(target, Replace.VARIABLE), type);
   }

   private Annotation<O> getFrom(final List<?> modifiers, final AnnotationFactory annotationFactory, final String type)
   {
      @SuppressWarnings("unchecked")
      Iterator<IExtendedModifier> iter = (Iterator<IExtendedModifier>) modifiers.iterator();
      while (iter.hasNext())
      {
         IExtendedModifier node = iter.next();
         if (node.isAnnotation())
         {
            final Annotation<O> wrapped = annotationFactory
                     .createAnnotationFor((org.eclipse.jdt.core.dom.Annotation) node);
            if (Types.areEquivalent(type, wrapped.getName()))
            {
               return wrapped;
            }
         }
      }
      return null;
   }

}
