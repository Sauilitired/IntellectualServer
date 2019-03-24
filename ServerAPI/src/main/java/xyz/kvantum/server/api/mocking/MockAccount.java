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
package xyz.kvantum.server.api.mocking;

import lombok.Data;
import lombok.Getter;
import xyz.kvantum.server.api.account.AccountExtension;
import xyz.kvantum.server.api.account.IAccount;
import xyz.kvantum.server.api.account.IAccountManager;
import xyz.kvantum.server.api.account.roles.AccountRole;
import xyz.kvantum.server.api.pojo.KvantumPojo;
import xyz.kvantum.server.api.pojo.KvantumPojoFactory;

import java.util.*;

@Data @SuppressWarnings("unused") public class MockAccount implements IAccount {

    @Getter private static final KvantumPojoFactory<IAccount> kvantumPojoFactory =
        KvantumPojoFactory.forClass(IAccount.class);

    private static final Random random = new Random();

    private Map<String, String> rawData = new HashMap<>();
    private int id = random.nextInt(10000);
    private String username = UUID.randomUUID().toString();
    private IAccountManager manager;
    private Collection<AccountRole> accountRoles = new HashSet<>();
    private Map<Class<? extends AccountExtension>, AccountExtension> extensions = new HashMap<>();

    @Override public void internalMetaUpdate(final String key, final String value) {
        this.rawData.put("meta." + key, value);
    }

    @Override public boolean passwordMatches(final String password) {
        return true;
    }

    @Override public Optional<String> getData(final String key) {
        return Optional.ofNullable(rawData.getOrDefault(key, null));
    }

    @Override public <T extends AccountExtension> T attachExtension(Class<T> extension) {
        final T instance = AccountExtension.createInstance(extension);
        this.extensions.put(extension, instance);
        return instance;
    }

    @Override @SuppressWarnings("ALL")
    public <T extends AccountExtension> Optional<T> getExtension(Class<T> extension) {
        final Object extensionInstance = this.extensions.get(extension);
        if (extensionInstance == null) {
            return Optional.empty();
        }
        return Optional.of((T) extensionInstance);
    }

    @Override public void saveState() {
    }

    @Override public void setData(final String key, final String value) {
        this.rawData.put(key, value);
    }

    @Override public void removeData(final String key) {
        this.rawData.remove(key);
    }

    @Override public void addRole(final AccountRole role) {
        this.accountRoles.add(role);
    }

    @Override public void removeRole(final AccountRole role) {
        this.accountRoles.remove(role);
    }

    @Override public String getSuppliedPassword() {
        return "";
    }

    @Override public boolean isPermitted(final String permissionKey) {
        return true;
    }

    @Override public KvantumPojo<IAccount> toKvantumPojo() {
        return kvantumPojoFactory.of(this);
    }
}
