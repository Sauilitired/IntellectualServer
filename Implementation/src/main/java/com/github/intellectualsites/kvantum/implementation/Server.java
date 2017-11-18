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
package com.github.intellectualsites.kvantum.implementation;

import com.github.intellectualsites.kvantum.api.account.IAccountManager;
import com.github.intellectualsites.kvantum.api.cache.ICacheManager;
import com.github.intellectualsites.kvantum.api.config.*;
import com.github.intellectualsites.kvantum.api.core.Kvantum;
import com.github.intellectualsites.kvantum.api.core.ServerImplementation;
import com.github.intellectualsites.kvantum.api.core.WorkerProcedure;
import com.github.intellectualsites.kvantum.api.events.Event;
import com.github.intellectualsites.kvantum.api.events.EventCaller;
import com.github.intellectualsites.kvantum.api.events.EventManager;
import com.github.intellectualsites.kvantum.api.events.defaultevents.ServerReadyEvent;
import com.github.intellectualsites.kvantum.api.events.defaultevents.ShutdownEvent;
import com.github.intellectualsites.kvantum.api.events.defaultevents.StartupEvent;
import com.github.intellectualsites.kvantum.api.events.defaultevents.ViewsInitializedEvent;
import com.github.intellectualsites.kvantum.api.fileupload.KvantumFileUpload;
import com.github.intellectualsites.kvantum.api.jtwig.JTwigEngine;
import com.github.intellectualsites.kvantum.api.logging.*;
import com.github.intellectualsites.kvantum.api.matching.Router;
import com.github.intellectualsites.kvantum.api.plugin.PluginLoader;
import com.github.intellectualsites.kvantum.api.plugin.PluginManager;
import com.github.intellectualsites.kvantum.api.request.AbstractRequest;
import com.github.intellectualsites.kvantum.api.request.PostProviderFactory;
import com.github.intellectualsites.kvantum.api.response.Response;
import com.github.intellectualsites.kvantum.api.session.ISession;
import com.github.intellectualsites.kvantum.api.session.ISessionCreator;
import com.github.intellectualsites.kvantum.api.session.ISessionDatabase;
import com.github.intellectualsites.kvantum.api.session.SessionManager;
import com.github.intellectualsites.kvantum.api.templates.TemplateManager;
import com.github.intellectualsites.kvantum.api.util.*;
import com.github.intellectualsites.kvantum.api.util.AutoCloseable;
import com.github.intellectualsites.kvantum.api.views.*;
import com.github.intellectualsites.kvantum.api.views.requesthandler.SimpleRequestHandler;
import com.github.intellectualsites.kvantum.crush.CrushEngine;
import com.github.intellectualsites.kvantum.files.Extension;
import com.github.intellectualsites.kvantum.files.FileSystem;
import com.github.intellectualsites.kvantum.files.Path;
import com.github.intellectualsites.kvantum.implementation.commands.AccountCommand;
import com.github.intellectualsites.kvantum.implementation.config.TranslationFile;
import com.github.intellectualsites.kvantum.implementation.error.KvantumException;
import com.github.intellectualsites.kvantum.implementation.error.KvantumInitializationException;
import com.github.intellectualsites.kvantum.implementation.error.KvantumStartException;
import com.github.intellectualsites.kvantum.implementation.mongo.MongoAccountManager;
import com.github.intellectualsites.kvantum.implementation.mongo.MongoSessionDatabase;
import com.github.intellectualsites.kvantum.implementation.sqlite.SQLiteAccountManager;
import com.github.intellectualsites.kvantum.implementation.sqlite.SQLiteSessionDatabase;
import com.github.intellectualsites.kvantum.implementation.tempfiles.TempFileManagerFactory;
import com.github.intellectualsites.kvantum.velocity.VelocityEngine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellectualsites.commands.CommandManager;
import com.intellectualsites.configurable.ConfigurationFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;
import sun.misc.Signal;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.*;
import java.net.BindException;
import java.net.ServerSocket;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Main {@link Kvantum} implementation.
 * <p>
 * Use {@link ServerImplementation#getImplementation()} to get an instance, rather
 * than {@link #getInstance()}.
 * </p>
 */
public final class Server implements Kvantum, ISessionCreator
{

    @SuppressWarnings("ALL")
    @Getter(AccessLevel.PACKAGE)
    private static Server instance;
    @Getter
    private final LogWrapper logWrapper;
    @Getter
    private final boolean standalone;
    @Getter
    private final Map<String, Class<? extends View>> viewBindings = new HashMap<>();
    @Getter
    private final WorkerProcedure procedure = new WorkerProcedure();
    @Getter
    private final SocketHandler socketHandler;
    @Getter
    private final Metrics metrics = new Metrics();
    @Getter
    private final ITempFileManagerFactory tempFileManagerFactory = new TempFileManagerFactory();
    @Getter
    private final KvantumFileUpload globalFileUpload = new KvantumFileUpload();
    @Getter
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter( Account.class, new AccountSerializer() ).create();
    PrintStream logStream;
    @Getter
    private volatile ICacheManager cacheManager;
    @Getter
    private boolean silent = false;
    @Getter
    private Router router;
    @Getter
    private SessionManager sessionManager;
    @Getter
    private ConfigurationFile translations;
    @Getter
    private File coreFolder;
    @Getter
    private boolean paused = false;
    @Getter
    private boolean stopping;
    @Getter
    private boolean started;
    private ServerSocket serverSocket;
    private SSLServerSocket sslServerSocket;
    private ConfigurationFile configViews;
    @Setter
    @Getter
    private EventCaller eventCaller;
    @Getter
    private FileSystem fileSystem;
    @Getter
    private CommandManager commandManager;
    @Getter
    private ApplicationStructure applicationStructure;

    /**
     * @param serverContext ServerContext that will be used to initialize the server
     * @throws KvantumException If anything was to fail
     */
    Server(final ServerContext serverContext)
            throws KvantumException
    {
        this.coreFolder = serverContext.getCoreFolder();
        this.logWrapper = serverContext.getLogWrapper();
        this.standalone = serverContext.isStandalone();
        this.router = serverContext.getRouter();

        //
        // Setup singleton references
        //
        InstanceFactory.setupInstanceAutomagic( this );
        ServerImplementation.registerServerImplementation( this );

        //
        // Setup and initialize the file system
        //
        coreFolder = new File( coreFolder, "kvantum" ); // Makes everything more portable
        if ( !coreFolder.exists() && !coreFolder.mkdirs() )
        {
            throw new KvantumInitializationException( "Failed to create the core folder: " + coreFolder );
        }
        this.fileSystem = new IntellectualFileSystem( coreFolder.toPath() );
        final File logFolder = FileUtils.attemptFolderCreation( new File( coreFolder, "log" ) );
        try
        {
            FileUtils.addToZip( new File( logFolder, "old.zip" ),
                    logFolder.listFiles( (dir, name) -> name.endsWith( ".txt" ) ) );
        } catch ( final Exception e )
        {
            e.printStackTrace();
        }

        //
        // Setup the log-to-file system and the error stream
        //
        try
        {
            this.logStream = new LogStream( logFolder );
            System.setErr( new PrintStream( new ErrorOutputStream( logWrapper ), true ) );
        } catch ( FileNotFoundException e )
        {
            e.printStackTrace();
        }

        //
        // Print license information
        //
        this.printLicenseInfo();

        //
        // Add default view bindings
        //
        this.addDefaultViewBindings();

        //
        // Setup the translation configuration file
        //
        try
        {
            translations = new TranslationFile( new File( coreFolder, "config" ) );
        } catch ( final Exception e )
        {
            log( Message.CANNOT_LOAD_TRANSLATIONS );
            e.printStackTrace();
        }

        //
        // Load the configuration file
        //
        if ( !CoreConfig.isPreConfigured() )
        {
            ConfigurationFactory.load( CoreConfig.class, new File( coreFolder, "config" ) ).get();
        }

        this.logWrapper.setFormat( CoreConfig.Logging.logFormat );

        //
        // Check through the configuration file and make sure that the values
        // are not weird
        //
        validateConfiguration();

        //
        // Enable the custom security manager
        //
        if ( CoreConfig.enableSecurityManager )
        {
            try
            {
                System.setOut( ServerSecurityManager.SecurePrintStream.construct( System.out ) );
            } catch ( Exception e )
            {
                e.printStackTrace();
            }
        }

        //
        // Send a sample debug message
        //
        if ( CoreConfig.debug )
        {
            log( Message.DEBUG );
        }

        //
        // Setup the internal engine
        //
        this.socketHandler = new SocketHandler();
        WorkerPool.setupPool( CoreConfig.workers );

        //
        // Setup default flags
        //
        this.started = false;
        this.stopping = false;

        //
        // Setup the cache manager
        //
        this.cacheManager = new CacheManager();

        //
        // Load view configuration
        //
        if ( !CoreConfig.disableViews )
        {
            this.loadViewConfiguration();
        }

        //
        // Setup the internal application
        //
        if ( standalone )
        {
            // Makes the application closable in ze terminal
            Signal.handle( new Signal( "INT" ), new ExitSignalHandler() );

            this.commandManager = new CommandManager( '/' );
            this.commandManager.getManagerOptions().getFindCloseMatches( false );
            this.commandManager.getManagerOptions().setRequirePrefix( false );

            switch ( CoreConfig.Application.databaseImplementation )
            {
                case "sqlite":
                    applicationStructure = new SQLiteApplicationStructure( "core" )
                    {
                        @Override
                        public IAccountManager createNewAccountManager()
                        {
                            return new SQLiteAccountManager( this );
                        }
                    };
                    break;
                case "mongo":
                    applicationStructure = new MongoApplicationStructure( "core" )
                    {
                        @Override
                        public IAccountManager createNewAccountManager()
                        {
                            return new MongoAccountManager( this );
                        }
                    };
                    break;
                default:
                    Message.DATABASE_UNKNOWN.log( CoreConfig.Application.databaseImplementation );
                    throw new KvantumInitializationException( "Cannot load - Invalid session database " +
                            "implementation provided" );
            }
        } else if ( !CoreConfig.Application.main.isEmpty() )
        {
            try
            {
                Class temp = Class.forName( CoreConfig.Application.main );
                if ( temp.getSuperclass().equals( ApplicationStructure.class ) )
                {
                    this.applicationStructure = (ApplicationStructure) temp.newInstance();
                } else
                {
                    log( Message.APPLICATION_DOES_NOT_EXTEND, CoreConfig.Application.main );
                }
            } catch ( ClassNotFoundException e )
            {
                log( Message.APPLICATION_CANNOT_FIND, CoreConfig.Application.main );
                e.printStackTrace();
            } catch ( InstantiationException | IllegalAccessException e )
            {
                log( Message.APPLICATION_CANNOT_INITIATE, CoreConfig.Application.main );
                e.printStackTrace();
            }
        }

        try
        {
            this.getApplicationStructure().getAccountManager().setup();
            if ( this.getCommandManager() != null )
            {
                getCommandManager().createCommand( new AccountCommand( applicationStructure ) );
            }
        } catch ( Exception e )
        {
            e.printStackTrace();
        }

        //
        // Load the session database
        //
        final ISessionDatabase sessionDatabase;
        if ( CoreConfig.Sessions.enableDb )
        {
            switch ( CoreConfig.Application.databaseImplementation.toLowerCase() )
            {
                case "sqlite":
                    sessionDatabase = new SQLiteSessionDatabase( (SQLiteApplicationStructure) this
                            .getApplicationStructure().getAccountManager()
                            .getApplicationStructure() );
                    break;
                case "mongo":
                    sessionDatabase = new MongoSessionDatabase( (MongoApplicationStructure) this
                            .getApplicationStructure().getAccountManager()
                            .getApplicationStructure() );
                    break;
                default:
                    Message.DATABASE_SESSION_UNKNOWN.log( CoreConfig.Application.databaseImplementation );
                    sessionDatabase = new DumbSessionDatabase();
                    break;
            }
            try
            {
                sessionDatabase.setup();
            } catch ( Exception e )
            {
                e.printStackTrace();
            }
        } else
        {
            sessionDatabase = new DumbSessionDatabase();
        }

        //
        // Setup the session manager implementation
        //
        this.sessionManager = new SessionManager( this, sessionDatabase );
    }

    private void validateConfiguration()
    {
        if ( CoreConfig.Buffer.in < 1000 || CoreConfig.Buffer.in > 100000 )
        {
            Logger.warn( "It is recommended to keep 'buffer.in' in server.yml between 0 and 100000" );
            Logger.warn( "Any other values may cause the server to run slower than expected" );
        }
        if ( CoreConfig.Buffer.out < 1000 || CoreConfig.Buffer.out > 100000 )
        {
            Logger.warn( "It is recommended to keep 'buffer.out' in server.yml between 0 and 100000" );
            Logger.warn( "Any other values may cause the server to run slower than expected" );
        }
    }

    private void loadViewConfiguration() throws KvantumException
    {
        try
        {
            configViews = new YamlConfiguration( "views", new File( new File( coreFolder, "config" ),
                    "views.yml" ) );
            configViews.loadFile();

            // These are the default views

            if ( !configViews.contains( "views" ) )
            {
                final YamlConfiguration standardFile = new YamlConfiguration( "views",
                        new File( new File( coreFolder, "config" ), "standardViews.yml" ) );
                standardFile.loadFile();

                {
                    Map<String, Object> views = new HashMap<>();
                    final Map<String, Object> defaultView = MapBuilder.<String, Object>newHashMap()
                            .put( "filter", "[file=index].[extension=html]" )
                            .put( "type", "std" )
                            .put( "options", MapBuilder.<String, Object>newHashMap()
                                    .put( "folder", "./public" )
                                    .put( "excludeExtensions", Collections.singletonList( "txt" ) )
                                    .put( "filePattern", "${file}.${extension}" ).get()
                            ).get();
                    views.put( "std", defaultView );
                    standardFile.setIfNotExists( "views", views );
                    standardFile.saveFile();
                }

                {
                    Map<String, Object> views = new HashMap<>();
                    views.put( "include", "standardViews.yml" );
                    configViews.setIfNotExists( "views", views );
                    configViews.saveFile();
                }
            }

            final Path path = getFileSystem().getPath( "public" );
            if ( !path.exists() )
            {
                path.create();
                //
                // Only create the default files if the /public folder doesn't exist
                //
                if ( !path.getPath( "favicon.ico" ).exists() )
                {
                    Message.CREATING.log( "public/favicon.ico" );
                    try ( OutputStream out = new FileOutputStream( new File( path.getJavaPath().toFile(),
                            "favicon.ico" )
                    ) )
                    {
                        FileUtils.copyFile( getClass().getResourceAsStream( "/template/favicon.ico" ), out, 1024 * 16 );
                    } catch ( final Exception e )
                    {
                        e.printStackTrace();
                    }
                }

                if ( !path.getPath( "index", Extension.HTML ).exists() )
                {
                    Message.CREATING.log( "public/index.html" );
                    try ( OutputStream out = new FileOutputStream( new File( path.getJavaPath().toFile(), "index.html" )
                    ) )
                    {
                        FileUtils.copyFile( getClass().getResourceAsStream( "/template/index.html" ), out, 1024 * 16 );
                    } catch ( final Exception e )
                    {
                        e.printStackTrace();
                    }
                }
            }
        } catch ( final Exception e )
        {
            throw new KvantumInitializationException( "Couldn't load in views", e );
        }
    }

    private void addDefaultViewBindings()
    {
        addViewBinding( "html", HTMLView.class );
        addViewBinding( "css", CSSView.class );
        addViewBinding( "javascript", JSView.class );
        addViewBinding( "less", LessView.class );
        addViewBinding( "img", ImgView.class );
        addViewBinding( "download", DownloadView.class );
        addViewBinding( "std", StandardView.class );
    }

    private void printLicenseInfo()
    {
        final LogWrapper.LogEntryFormatter prefix = msg -> "> " + msg;
        logWrapper.log( ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>" );
        LambdaUtil.arrayForeach( string -> logWrapper.log( prefix, string ),
                "APACHE LICENSE VERSION 2.0:",
                "",
                "Kvantum, Copyright (C) 2017 IntellectualSites",
                "Kvantum comes with ABSOLUTELY NO WARRANTY; for details type `/show w`",
                "This is free software, and you are welcome to redistribute it",
                "under certain conditions; type `/show c` for details.",
                ""
        );
        logWrapper.log( ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>" );
        logWrapper.log();
    }

    @Synchronized
    @Override
    public void addViewBinding(final String key, final Class<? extends View> c)
    {
        Assert.notNull( c );
        Assert.notEmpty( key );

        viewBindings.put( key, c );
    }

    @SuppressWarnings("ALL")
    @Override
    public void validateViews()
    {
        final List<String> toRemove = new ArrayList<>();
        for ( final Map.Entry<String, Class<? extends View>> e : viewBindings.entrySet() )
        {
            final Class<? extends View> vc = e.getValue();
            try
            {
                vc.getDeclaredConstructor( String.class, Map.class );
            } catch ( final Exception ex )
            {
                log( Message.INVALID_VIEW, e.getKey() );
                toRemove.add( e.getKey() );
            }
        }
        toRemove.forEach( viewBindings::remove );
    }

    @Synchronized
    @Override
    public void handleEvent(final Event event)
    {
        Assert.notNull( event );

        if ( standalone || eventCaller == null )
        {
            EventManager.getInstance().handle( event );
        } else
        {
            eventCaller.callEvent( event );
        }
    }

    @Override
    public void loadPlugins()
    {
        if ( standalone )
        {
            final File file = new File( coreFolder, "plugins" );
            if ( !file.exists() && !file.mkdirs() )
            {
                log( Message.COULD_NOT_CREATE_PLUGIN_FOLDER, file );
                return;
            }
            PluginLoader pluginLoader = new PluginLoader( new PluginManager() );
            pluginLoader.loadAllPlugins( file );
            pluginLoader.enableAllPlugins();
        } else
        {
            log( Message.STANDALONE_NOT_LOADING_PLUGINS );
        }
    }

    @SuppressWarnings("ALL")
    @Override
    @Synchronized
    public void start()
    {
        try
        {
            Assert.equals( this.started, false,
                    new KvantumStartException( "Cannot start the server, it is already started",
                            new KvantumException( "Cannot restart server singleton" ) ) );
        } catch ( KvantumStartException e )
        {
            e.printStackTrace();
            return;
        }

        TemplateManager.get().addProviderFactory( ServerImplementation.getImplementation().getSessionManager() );
        TemplateManager.get().addProviderFactory( ConfigVariableProvider.getInstance() );
        TemplateManager.get().addProviderFactory( new PostProviderFactory() );
        TemplateManager.get().addProviderFactory( new MetaProvider() );

        Logger.info( "Checking templating engines:" );
        CrushEngine.getInstance().load();
        VelocityEngine.getInstance().load();
        JTwigEngine.getInstance().load();
        Logger.info( "" );

        // Load Plugins
        this.loadPlugins();
        EventManager.getInstance().bake();

        this.log( Message.CALLING_EVENT, "startup" );
        this.handleEvent( new StartupEvent( this ) );

        // Validating views
        this.log( Message.VALIDATING_VIEWS );
        this.validateViews();

        if ( !CoreConfig.disableViews )
        {
            this.log( Message.LOADING_VIEWS );
            this.log( "" );
            new ViewLoader( configViews );
        } else
        {
            Message.VIEWS_DISABLED.log();
        }

        this.applicationStructure.registerViews( this );
        this.handleEvent( new ViewsInitializedEvent( this ) );

        router.dump( this );

        if ( !CoreConfig.Cache.enabled )
        {
            log( Message.CACHING_DISABLED );
        } else
        {
            log( Message.CACHING_ENABLED );
        }

        log( "" );

        log( Message.STARTING_ON_PORT, CoreConfig.port );

        if ( standalone && CoreConfig.enableInputThread )
        {
            new InputThread().start();
        }

        this.started = true;
        try
        {
            serverSocket = new ServerSocket( CoreConfig.port );
            log( Message.SERVER_STARTED );
        } catch ( final Exception e )
        {
            boolean run = true;

            int port = CoreConfig.port;
            while ( run )
            {
                try
                {
                    serverSocket = new ServerSocket( ++port );
                    run = false;
                    Message.PORT_OCCUPIED.log( port );
                    CoreConfig.port = port;
                } catch ( final BindException ex )
                {
                    continue;
                } catch ( final Exception ex )
                {
                    ex.printStackTrace();
                }
            }
        }

        log( "" );

        if ( CoreConfig.SSL.enable )
        {
            log( Message.STARTING_SSL_ON_PORT, CoreConfig.SSL.port );
            try
            {
                System.setProperty( "javax.net.ssl.keyStore", CoreConfig.SSL.keyStore );
                System.setProperty( "javax.net.ssl.keyStorePassword", CoreConfig.SSL.keyStorePassword );

                final SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
                sslServerSocket = (SSLServerSocket) factory.createServerSocket( CoreConfig.SSL.port );
            } catch ( final Exception e )
            {
                new KvantumException( "Failed to start HTTPS server", e ).printStackTrace();
            }
        }

        log( Message.ACCEPTING_CONNECTIONS_ON, CoreConfig.hostname +
                ( CoreConfig.port == 80 ? "" : ":" + CoreConfig.port ) + "/'" );
        log( Message.OUTPUT_BUFFER_INFO, CoreConfig.Buffer.out / 1024, CoreConfig.Buffer.in / 1024 );

        this.handleEvent( new ServerReadyEvent( this ) );

        if ( CoreConfig.SSL.enable && sslServerSocket != null )
        {
            new HTTPSThread( sslServerSocket, socketHandler ).start();
        }

        new HTTPThread( serverSocket, socketHandler ).start();
    }

    @Override
    public void log(final Message message, final Object... args)
    {
        this.log( message.toString(), message.getMode(), args );
    }

    @Override
    public void log(final String message, final int mode, final Object... args)
    {
        // This allows us to customize what messages are
        // sent to the logging screen, and thus we're able
        // to limit to only error messages or such
        if ( ( mode == LogModes.MODE_DEBUG && !CoreConfig.debug ) || mode < LogModes.lowestLevel || mode > LogModes
                .highestLevel )
        {
            return;
        }
        String prefix;
        switch ( mode )
        {
            case LogModes.MODE_DEBUG:
                prefix = "Debug";
                break;
            case LogModes.MODE_INFO:
                prefix = "Info";
                break;
            case LogModes.MODE_ERROR:
                prefix = "Error";
                break;
            case LogModes.MODE_WARNING:
                prefix = "Warning";
                break;
            default:
                prefix = "Info";
                break;
        }

        this.log( prefix, message, args );
    }

    @Synchronized
    private void log(final String prefix, final String message, final Object... args)
    {
        String msg = message;
        for ( final Object a : args )
        {
            String objectString;
            if ( a == null )
            {
                objectString = "null";
            } else if ( a instanceof LogFormatted )
            {
                objectString = ( (LogFormatted) a ).getLogFormatted();
            } else
            {
                objectString = a.toString();
            }
            msg = msg.replaceFirst( "%s", objectString );
        }

        logWrapper.log( LogContext.builder().applicationPrefix( CoreConfig.logPrefix ).logPrefix( prefix ).timeStamp(
                TimeUtil.getTimeStamp() ).message( msg ).thread( Thread.currentThread().getName() ).build() );
    }

    @Override
    public void log(final String message, final Object... args)
    {
        this.log( message, LogModes.MODE_INFO, args );
    }

    @Override
    public void log(final LogProvider provider, final String message, final Object... args)
    {
        this.log( provider.getLogIdentifier(), message, args );
    }

    @Synchronized
    @Override
    public void stopServer()
    {
        Message.SHUTTING_DOWN.log();
        EventManager.getInstance().handle( new ShutdownEvent( this ) );

        try
        {
            serverSocket.close();
            if ( CoreConfig.SSL.enable )
            {
                sslServerSocket.close();
            }
        } catch ( final Exception e )
        {
            e.printStackTrace();
        }

        socketHandler.handleShutdown();

        AutoCloseable.closeAll();

        try
        {
            this.logStream.close();
        } catch ( final Exception e )
        {
            e.printStackTrace();
        }

        if ( standalone && CoreConfig.exitOnStop )
        {
            System.exit( 0 );
        }
    }

    @Override
    public RequestHandler createSimpleRequestHandler(final String filter, final BiConsumer<AbstractRequest, Response> generator)
    {
        return SimpleRequestHandler.builder().setPattern( filter ).setGenerator( generator )
                .build().addToRouter( router );
    }

    @Override
    public ISession createSession()
    {
        return new Session();
    }

}
