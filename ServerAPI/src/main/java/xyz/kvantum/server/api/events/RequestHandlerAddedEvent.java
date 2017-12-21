/*
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
package xyz.kvantum.server.api.events;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import pw.stamina.causam.event.Cancellable;
import xyz.kvantum.server.api.views.RequestHandler;

public final class RequestHandlerAddedEvent extends Event implements Cancellable
{

    @Getter
    private final RequestHandler context;
    @Getter
    @Setter
    private boolean cancelled = false;

    public RequestHandlerAddedEvent(@NonNull final RequestHandler context)
    {
        super( "requestHandlerAddedEvent" );
        this.context = context;
    }

    @Override
    public void cancel()
    {
        this.setCancelled( true );
    }
}
