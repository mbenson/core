/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.forge.container;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarFile;

import org.jboss.forge.container.InstalledPluginRegistry.PluginEntry;
import org.jboss.forge.container.exception.ContainerException;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ModuleSpec.Builder;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.ResourceLoaders;

/**
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 * 
 */
public class PluginModuleLoader extends ModuleLoader
{
   private static final ModuleIdentifier PLUGIN_CONTAINER_API = ModuleIdentifier.create("org.jboss.forge.api");
   private static final ModuleIdentifier WELD = ModuleIdentifier.create("org.jboss.weld");
   
   private List<PluginEntry> installed;
   private ModuleLoader parent;

   public PluginModuleLoader(List<PluginEntry> installed)
   {
      this.installed = installed;
      this.parent = Module.getBootModuleLoader();
   }

   @Override
   protected Module preloadModule(ModuleIdentifier identifier) throws ModuleLoadException
   {
      if (findModule(identifier) != null)
      {
         Module pluginModule = super.preloadModule(identifier);
         return pluginModule;
      }
      else
         return preloadModule(identifier, parent);
   }

   @Override
   protected ModuleSpec findModule(ModuleIdentifier id) throws ModuleLoadException
   {
      boolean found = false;
      for (PluginEntry plugin : installed)
      {
         if (plugin.toModuleId().equals(id.toString()))
         {
            found = true;
            break;
         }
      }

      if (found)
      {
         Builder specBuilder = ModuleSpec.build(id);
         specBuilder.addDependency(DependencySpec.createModuleDependencySpec(PLUGIN_CONTAINER_API));
         specBuilder.addDependency(DependencySpec.createModuleDependencySpec(WELD));

         try
         {
            specBuilder
                     .addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(ResourceLoaders
                              .createJarResourceLoader(
                                       "plugin.jar",
                                       new JarFile(
                                                new File(
                                                         "/Users/lbaxter/.forge/plugins/org/example/plugin/1.0.0-SNAPSHOT/plugin.jar")))));
            ModuleSpec moduleSpec = specBuilder.create();
            return moduleSpec;
         }
         catch (IOException e)
         {
            throw new ContainerException("Could not load plugin resource [*TODO*]", e);
         }
      }
      return null;
   }

   @Override
   public String toString()
   {
      return "Forge-plugin Module Loader";
   }

}
