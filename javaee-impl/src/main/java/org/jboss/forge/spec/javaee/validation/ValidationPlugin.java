/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.forge.spec.javaee.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import org.jboss.forge.parser.JavaParser;
import org.jboss.forge.parser.java.JavaAnnotation;
import org.jboss.forge.project.Project;
import org.jboss.forge.project.dependencies.Dependency;
import org.jboss.forge.project.dependencies.DependencyBuilder;
import org.jboss.forge.project.dependencies.DependencyInstaller;
import org.jboss.forge.project.dependencies.ScopeType;
import org.jboss.forge.project.facets.DependencyFacet;
import org.jboss.forge.project.facets.JavaSourceFacet;
import org.jboss.forge.project.facets.events.InstallFacets;
import org.jboss.forge.resources.java.JavaResource;
import org.jboss.forge.shell.PromptType;
import org.jboss.forge.shell.ShellPrompt;
import org.jboss.forge.shell.events.PickupResource;
import org.jboss.forge.shell.plugins.*;
import org.jboss.forge.spec.javaee.ValidationFacet;
import org.jboss.forge.spec.javaee.descriptor.ValidationDescriptor;
import org.jboss.forge.spec.javaee.validation.provider.BVProvider;
import org.jboss.forge.spec.javaee.validation.provider.ValidationProvider;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;

import javax.enterprise.event.Event;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.validation.Constraint;

import java.io.FileNotFoundException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Set;

/**
 * @author Kevin Pollet
 */
@Alias("validation")
@RequiresProject
@RequiresFacet(DependencyFacet.class)
public class ValidationPlugin implements Plugin
{
   private final Project project;
   private final BeanManager beanManager;
   private final Event<InstallFacets> request;
   private final ShellPrompt prompt;
   private final DependencyInstaller installer;
   private final Event<PickupResource> pickup;


   @Inject
   public ValidationPlugin(final Project project, final Event<InstallFacets> request, final BeanManager beanManager,
            final ShellPrompt prompt, final DependencyInstaller installer, final Event<PickupResource> pickup)
   {
      this.project = project;
      this.beanManager = beanManager;
      this.request = request;
      this.prompt = prompt;
      this.installer = installer;
      this.pickup = pickup;
   }

   @Command(value = "setup", help = "Setup validation for this project")
   public void setup(
            @Option(name = "provider", defaultValue = "HIBERNATE_VALIDATOR", required = true) final BVProvider providerType,
            @Option(name = "messageInterpolator", type = PromptType.JAVA_CLASS) final String messageInterpolator,
            @Option(name = "traversableResolver", type = PromptType.JAVA_CLASS) final String traversableResolver,
            @Option(name = "constraintValidatorFactory", type = PromptType.JAVA_CLASS) final String constraintValidatorFactory)
   {
      // instantiates the validation provider specified by the user
      final ValidationProvider provider = providerType.getValidationProvider(beanManager);

      if (!project.hasFacet(ValidationFacet.class))
      {
         request.fire(new InstallFacets(ValidationFacet.class));
      }

      installDependencies(provider.getDependencies());

      if (!provider.getAdditionalDependencies().isEmpty())
      {
         if (prompt.promptBoolean("Would you install " + providerType.getName() + " additional dependencies?", false)) {
            installDependencies(provider.getAdditionalDependencies());
         }
      }

      if (provider.getDefaultDescriptor() != null)
      {
         final ValidationDescriptor providerDescriptor = provider.getDefaultDescriptor();
         final ValidationDescriptor descriptor = Descriptors.create(ValidationDescriptor.class)
                  .setDefaultProvider(providerDescriptor.getDefaultProvider())
                  .setMessageInterpolator( messageInterpolator == null ? providerDescriptor.getMessageInterpolator() : messageInterpolator)
                  .setTraversableResolver( traversableResolver == null ? providerDescriptor.getTraversableResolver() : traversableResolver)
                  .setConstraintValidatorFactory( constraintValidatorFactory == null ? providerDescriptor.getConstraintValidatorFactory() : constraintValidatorFactory);
         
         project.getFacet(ValidationFacet.class).saveConfig(descriptor);
      }

   }

   @Command("new-constraint-type")
   public void newConstraintType(
            @Option(required = true,
                     name = "type") final JavaResource resource,
            @Option(required = false, name = "overwrite") final boolean overwrite
            ) throws FileNotFoundException
   {
      if (!resource.exists() || overwrite)
      {
         JavaSourceFacet java = project.getFacet(JavaSourceFacet.class);
         if (resource.createNewFile())
         {
            JavaAnnotation constraint = JavaParser.create(JavaAnnotation.class);
            constraint.setName(java.calculateName(resource));
            constraint.setPackage(java.calculatePackage(resource));
            constraint.addAnnotation(Constraint.class);
            constraint.addAnnotation(Retention.class).setEnumValue(RUNTIME);
            constraint.addAnnotation(Target.class).setEnumValue(METHOD, FIELD, PARAMETER, TYPE);
            
            resource.setContents(constraint);
            pickup.fire(new PickupResource(resource));
         }
      }
      else
      {
         throw new RuntimeException("Type already exists [" + resource.getFullyQualifiedName()
                  + "] Re-run with '--overwrite' to continue.");
      }
   }

   private void installDependencies(final Set<Dependency> dependencies)
   {
      for (Dependency dep : dependencies)
      {
         if (!installer.isInstalled(project, dep))
         {
             dep = DependencyBuilder.create(dep).setScopeType(promptForScope(dep));
             installer.install(project, dep);
         }
      }
   }

    private ScopeType promptForScope(Dependency dep) {
        boolean answer = prompt.promptBoolean("Should the dependency be packaged with your application (not provided by the server)?", false);
        return answer ? ScopeType.COMPILE : ScopeType.PROVIDED;
    }
}
