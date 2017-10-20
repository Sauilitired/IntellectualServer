/*
 * IntellectualServer is a web server, written entirely in the Java language.
 * Copyright (C) 2017 IntellectualSites
 *
 * This program is free software; you can redistribute it andor modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package example;

import com.github.intellectualsites.iserver.api.core.ServerImplementation;
import com.github.intellectualsites.iserver.api.plugin.Plugin;
import com.github.intellectualsites.iserver.api.request.Request;
import com.github.intellectualsites.iserver.api.response.Response;
import com.github.intellectualsites.iserver.api.views.staticviews.StaticViewManager;
import com.github.intellectualsites.iserver.api.views.staticviews.ViewMatcher;

public class ExamplePlugin extends Plugin
{

    @Override
    protected void onEnable()
    {
        try
        {
            StaticViewManager.generate( ExamplePlugin.this );
        } catch ( final Exception e )
        {
            e.printStackTrace();
        }
        ServerImplementation.getImplementation().createSimpleRequestHandler( "plugin", ( (request, response) ->
                response.setContent( "The Plugin Works, Mate!" ) ) );
    }

    @ViewMatcher(filter = "test", cache = false, name = "testPlugin")
    public void testFoodie(final Request request, final Response response)
    {
        response.setContent( "This Plugin Loads, Mate!" );
    }
}
