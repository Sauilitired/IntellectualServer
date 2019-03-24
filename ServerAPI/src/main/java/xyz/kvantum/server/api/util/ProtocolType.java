/*
 *    _  __                     _
 *    | |/ /__   __ __ _  _ __  | |_  _   _  _ __ ___
 *    | ' / \ \ / // _` || '_ \ | __|| | | || '_ ` _ \
 *    | . \  \ V /| (_| || | | || |_ | |_| || | | | | |
 *    |_|\_\  \_/  \__,_||_| |_| \__| \__,_||_| |_| |_|
 *
 *    Copyright (C) 2019 Alexander Söderberg
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
package xyz.kvantum.server.api.util;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Protocol implementation enum
 */
@RequiredArgsConstructor public enum ProtocolType {

    HTTP("http"), HTTPS("https");

    private static Map<String, ProtocolType> CACHE;

    @Getter private final String string;

    /**
     * Match a string to a {@link ProtocolType}, if possible
     *
     * @param string String to match, may not be null
     * @return matched protocol type if found
     */
    @Nonnull public static Optional<ProtocolType> getByName(@Nonnull @NonNull final String string) {
        Assert.notEmpty(string);

        if (CACHE == null) {
            CACHE = new HashMap<>();
            for (final ProtocolType type : values()) {
                CACHE.put(type.name(), type);
            }
        }

        final String fixed = string.replaceAll("\\s", "").toUpperCase(Locale.ENGLISH);
        return Optional.of(CACHE.get(fixed));
    }

}
