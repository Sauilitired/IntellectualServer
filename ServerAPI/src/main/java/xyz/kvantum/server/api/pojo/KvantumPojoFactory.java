package xyz.kvantum.server.api.pojo;

import com.google.common.collect.ImmutableMap;
import com.hervian.lambda.LambdaFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Class responsible for constructing {@link KvantumPojo} instances
 *
 * @param <Object> POJO class
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings({ "WeakerAccess", "unused" })
public final class KvantumPojoFactory<Object>
{

    private final Map<String, PojoGetter<Object>> getters;
    private final Map<String, PojoSetter<Object>> setters;

    @Getter
    private final PojoXmlFactory<Object> xmlFactory = new PojoXmlFactory<>( this );
    @Getter
    private final PojoJsonFactory<Object> jsonFactory = new PojoJsonFactory<>( this );
    @Getter
    private final Function<Object, KvantumPojo<? extends Object>> mapper = this::of;

    /**
     * Construct a new {@link KvantumPojoFactory} for a given POJO class
     *
     * @param pojoClass Class containing Object
     * @param <Object>  Class Type
     * @return Constructed Factory
     */
    public static <Object> KvantumPojoFactory<Object> forClass(@NonNull final Class<Object> pojoClass)
    {
        final ImmutableMap.Builder<String, PojoGetter<Object>> getterBuilder = ImmutableMap.builder();
        final ImmutableMap.Builder<String, PojoSetter<Object>> setterBuilder = ImmutableMap.builder();

        for ( final Method method : pojoClass.getDeclaredMethods() )
        {
            final String prefix;
            boolean getter = false;

            if ( method.getName().startsWith( "is" ) )
            {
                prefix = "is";
                getter = true;
            } else if ( method.getName().startsWith( "get" ) )
            {
                if ( method.getName().equals( "get" ) )
                {
                    // "get" is often used to get underlying maps
                    continue;
                }
                prefix = "get";
                getter = true;
            } else if ( method.getName().startsWith( "set" ) )
            {
                prefix = "set";
            } else
            {
                continue;
            }

            if ( getter && method.getParameterCount() > 0 )
            {
                // make sure that we only include pure getters
                continue;
            }

            if ( !getter && method.getParameterCount() != 1 )
            {
                // make sure we only include pure setters
                continue;
            }

            //
            // Will turn "getName" into "name"
            //
            String name = method.getName().replaceFirst( prefix, "" );
            String firstChar = new String( new char[]{ name.charAt( 0 ) } );
            name = firstChar.toLowerCase( Locale.US ) + name.substring( 1 );

            if ( getter )
            {
                try
                {
                    getterBuilder.put( name, new PojoGetter<>( LambdaFactory.create( method ),
                            method.getReturnType() ) );
                } catch ( final Throwable throwable )
                {
                    throwable.printStackTrace();
                }
            } else
            {
                try
                {
                    setterBuilder.put( name, new PojoSetter<>( LambdaFactory.create( method ),
                            method.getParameters()[ 0 ].getType() ) );
                } catch ( final Throwable throwable )
                {
                    throwable.printStackTrace();
                }
            }
        }
        return new KvantumPojoFactory<>( getterBuilder.build(), setterBuilder.build() );
    }

    /**
     * Get a {@link KvantumPojo} instance for the given POJO instance
     *
     * @param instance POJO Instance
     * @return Instance
     */
    public KvantumPojo<Object> of(@NonNull final Object instance)
    {
        return new KvantumPojo<>( this, instance, getters, setters );
    }

    /**
     * Get a collection of {@link KvantumPojo} objects from a collection of
     * POJO objects
     *
     * @param collection Collection
     * @return Stream of {@link KvantumPojo} objects
     */
    public Collection<KvantumPojo> getPojoCollection(@NonNull final Collection<Object> collection)
    {
        if ( collection.isEmpty() )
        {
            return Collections.emptyList();
        }
        return collection.stream().map( this.mapper ).collect( Collectors.toCollection( ArrayList::new ) );
    }
}
