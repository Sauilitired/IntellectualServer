/*
 *
 *    Copyright (C) 2017 IntellectualSites
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.kvantum.server.api.plugin;

import org.yaml.snakeyaml.Yaml;
import sun.misc.JarFilter;
import xyz.kvantum.server.api.config.Message;
import xyz.kvantum.server.api.exceptions.PluginException;
import xyz.kvantum.server.api.util.Assert;
import xyz.kvantum.server.api.util.AutoCloseable;
import xyz.kvantum.server.api.util.FileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * A plugin loader
 * I borrwed from my
 * other project
 * (MarineStandalone)
 */
public class PluginLoader extends AutoCloseable
{

    private final ConcurrentMap<String, PluginClassLoader> loaders;
    private final ConcurrentMap<String, Class> classes;
    private final PluginManager manager;
    private final String[] BLOCKED_NAMES = new String[]{ "com.intellectualsites" };
    private Yaml yaml;

    /**
     * Constructor
     *
     * @param manager Related PluginManager
     */
    public PluginLoader(final PluginManager manager)
    {
        this.manager = manager;
        loaders = new ConcurrentHashMap<>();
        classes = new ConcurrentHashMap<>();
    }

    /**
     * Get the related plugin manager
     *
     * @return Plugin Manager specified in the constructor
     */
    public PluginManager getManager()
    {
        return manager;
    }

    /**
     * Used to check if the main file path of a plugin description file includes
     * a path which we have marked as illegal.
     * <p>
     * BY THE WAY, DON'T USE OUR FREAKING DOMAIN NAME...
     *
     * @param desc File to be checked
     * @throws PluginException If the file uses a bad name
     */
    public void checkIllegal(final PluginFile desc)
    {
        final String main = desc.mainClass;
        for ( final String blocked : BLOCKED_NAMES )
        {
            if ( main.toLowerCase().contains( blocked ) )
            {
                throw new PluginException( "Plugin " + desc.name + " contains illegal main path" );
            }
        }
    }

    /**
     * Load all plugins
     *
     * @param folder Folder to load from
     */
    public void loadAllPlugins(final File folder)
    {
        if ( !folder.exists() || !folder.isDirectory() )
            throw new IllegalArgumentException( folder.toString() + ", doesn't exist!" );
        final File[] files = folder.listFiles( file -> file.getName().endsWith( ".jar" ) );
        for ( final File file : files )
        {
            PluginClassLoader loader;
            try
            {
                loader = loadPlugin( file );
                // Make sure the path name is valid
                checkIllegal( loader.getDesc() );
                loader.create( loader.plugin );
                manager.addPlugin( loader.plugin );
                if ( new File( loader.getData(), "lib" ).exists() )
                {
                    final File[] fs = new File( loader.getData(), "lib" )
                            .listFiles( new JarFilter() );
                    for ( final File f : fs )
                        try
                        {
                            loader.loadJar( f );
                        } catch ( final Exception e )
                        {
                            e.printStackTrace();
                        }
                }
            } catch ( final Exception e )
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Enable all plugins
     */
    public void enableAllPlugins()
    {
        manager.getPlugins().forEach( this::enablePlugin );
    }

    /**
     * Disable all plugins
     */
    public void disableAllPlugins()
    {
        Message.DISABLING_PLUGINS.log();
        for ( final Plugin plugin : manager.getPlugins() )
            try
            {
                disablePlugin( plugin );
            } catch ( final Exception e )
            {
                e.printStackTrace();
            }
    }

    /**
     * Load a specific plugin
     *
     * @param file Jar File
     * @return Plugin Loaded
     */
    public PluginClassLoader loadPlugin(final File file) throws Exception
    {
        if ( !file.exists() )
        {
            throw new FileNotFoundException( file.getPath()
                    + " does not exist" );
        }
        final PluginFile desc = getPluginFile( file );
        Assert.equals( isTaken( desc.name ), false );
        final File parent = file.getParentFile();
        final File data = new File( parent, desc.name );
        if ( !data.exists() && !data.mkdir() )
        {
            throw new PluginException( "Couldn't create the data folder for " + desc.name );
        }
        copyConfigIfExists( file, data );
        PluginClassLoader loader;
        loader = new PluginClassLoader( this, desc, file );
        loaders.put( desc.name, loader );
        return loader;
    }

    /**
     * Get a class based on it's name
     *
     * @param name Of the class
     * @return Class if found, else null
     */
    protected Class<?> getClassByName(final String name)
    {
        if ( classes.containsKey( name ) )
            return classes.get( name );
        Class clazz;
        PluginClassLoader loader;
        for ( final Map.Entry<String, PluginClassLoader> currentEntry : loaders.entrySet() )
        {
            loader = currentEntry.getValue();
            try
            {
                if ( ( clazz = loader.findClass( name, false ) ) != null )
                    return clazz;
            } catch ( final Exception e )
            {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Set a class (name) to a class (object)
     *
     * @param name  Class Name
     * @param clazz Class Object
     */
    protected void setClass(final String name, final Class clazz)
    {
        if ( !classes.containsKey( name ) )
            classes.put( name, clazz );
    }

    /**
     * Remove a class based on its name
     *
     * @param name Class Name
     */
    protected void removeClass(final String name)
    {
        if ( classes.containsKey( name ) )
            classes.remove( name );
    }

    /**
     * Enable a plugin
     *
     * @param plugin Plugin to enable
     */
    public void enablePlugin(final Plugin plugin)
    {
        if ( !plugin.isEnabled() )
        {
            final String name = plugin.getName();
            if ( !loaders.containsKey( name ) )
                loaders.put( name, plugin.getClassLoader() );
            manager.enablePlugin( plugin );
            plugin.log( plugin.getName() + " is enabled!" );
        }
    }

    /**
     * Disable a plugin
     *
     * @param plugin Plugin to disable
     * @throws java.lang.UnsupportedOperationException If the plugin is already disabled
     */
    public void disablePlugin(final Plugin plugin)
    {
        if ( plugin.isEnabled() )
        {
            manager.disablePlugin( plugin );
            loaders.remove( plugin.getName() );
            final PluginClassLoader loader = plugin.getClassLoader();
            loader.getClasses().forEach( this::removeClass );
            Message.DISABLED_PLUGIN.log( plugin );
        } else
            throw new UnsupportedOperationException(
                    "Cannot disable an already disabled plugin" );
    }

    /**
     * Copy the config and lib files from the plugin jar to a specified data
     * folder
     *
     * @param file        Jar File, which contains the files you want to copy
     * @param destination Destination Folder
     */
    private void copyConfigIfExists(final File file, final File destination) throws IOException
    {
        JarFile jar;
        jar = new JarFile( file );
        final Enumeration<JarEntry> entries = jar.entries();
        JarEntry entry;
        final List<JarEntry> entryList = new ArrayList<>();
        while ( entries.hasMoreElements() )
        {
            entry = entries.nextElement();
            if ( !entry.getName().equalsIgnoreCase( "desc.json" )
                    && ( entry.getName().endsWith( ".json" )
                    || entry.getName().endsWith( ".jar" )
                    || entry.getName().endsWith( ".properties" )
                    || entry.getName().endsWith( ".sql" ) || entry
                    .getName().endsWith( ".db" ) ) )
                entryList.add( entry );
        }
        for ( final JarEntry e : entryList )
            if ( !e.getName().endsWith( ".jar" ) )
            {
                if ( new File( destination, e.getName() ).exists() )
                    continue;
                try
                {
                    FileUtils.copyFile( jar.getInputStream( e ),
                            new BufferedOutputStream( new FileOutputStream(
                                    new File( destination, e.getName() ) ) ),
                            1024 * 1024 * 5 );
                } catch ( final IOException exz )
                {
                    exz.printStackTrace();
                }
            } else
            {
                final File lib = new File( destination, "lib" );
                if ( !lib.exists() && lib.mkdir() )
                {
                    continue;
                }
                if ( new File( lib, e.getName() ).exists() )
                {
                    continue;
                }
                try
                {
                    FileUtils.copyFile( jar.getInputStream( e ),
                            new BufferedOutputStream( new FileOutputStream(
                                    new File( lib, e.getName() ) ) ),
                            1024 * 1024 * 5 );
                } catch ( final IOException exz )
                {
                    exz.printStackTrace();
                }
            }
    }

    public Yaml getYaml()
    {
        if ( yaml == null )
        {
            yaml = new Yaml();
        }
        return yaml;
    }

    /**
     * Get a plugin description file from a jar file
     *
     * @param file File, should be a jarfile containing a "desc.json" and the
     *             source folder
     * @return Plugin Description file, or null (if it cannot be found) loaded
     * from the loaded from the input file
     */
    private PluginFile getPluginFile(final File file) throws Exception
    {
        JarFile jar;
        jar = new JarFile( file );
        final JarEntry desc = jar.getJarEntry( "desc.yml" );
        if ( desc == null )
        {
            throw new PluginException( "Couldn't find desc for " + file );
        }
        InputStream stream;
        stream = jar.getInputStream( desc );
        PluginFile f;
        f = new PluginFile( stream, getYaml() );
        try
        {
            jar.close();
        } catch ( final Exception e )
        {
            e.printStackTrace();
        }
        return f;
    }

    public boolean isTaken(final String provider)
    {
        for ( Plugin plugin : getManager().getPlugins() )
        {
            if ( plugin.toString().equals( provider ) )
            {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void handleClose()
    {
        this.disableAllPlugins();
    }
}
