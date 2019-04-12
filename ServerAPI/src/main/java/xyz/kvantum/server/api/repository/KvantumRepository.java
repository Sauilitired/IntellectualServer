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
package xyz.kvantum.server.api.repository;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Repository used internally throughout Kvantum.
 * <p>
 * A repository is any object that can hold objects that can be
 * accessed using a specified ID (of any given type ID). A repository
 * may (but does not necessarily have to) support the following actions:
 * <ul>
 * <li>Access all objects</li>
 * <li>Access objects by key</li>
 * <li>Access objects through queries</li>
 * <li>Insert objects</li>
 * <li>Delete objects</li>
 * </ul>
 *
 * @param <T>  Object type
 * @param <ID> ID type
 */
@SuppressWarnings("unused") public interface KvantumRepository<T, ID> {

    /**
     * Find all objects registered in the repository
     *
     * @return All objects without sorting
     */
    Collection<? extends T> findAll();

    /**
     * Find all objects registered in the repository and sort using a specified sorter
     *
     * @param sorter Sorter to sort the objects with
     * @return Sorted collection
     */
    default Collection<? extends T> findAllSorted(Sorter<T> sorter) {
        return sorter.sort(findAll());
    }

    /**
     * Find all objects that correspond to a list of given identifiers
     *
     * @param collection Identifiers
     * @return Immutable collection of matching items
     */
    Collection<? extends T> findAllById(Collection<ID> collection);

    /**
     * Find all objects that correspond to a list of given identifiers and sort using a specified sorter
     *
     * @param collection Identifiers
     * @param sorter     Sorter
     * @return Immutable collection of matching items
     */
    default Collection<? extends T> findAllByIdSorted(Collection<ID> collection,
        Sorter<T> sorter) {
        return sorter.sort(findAllById(collection));
    }

    /**
     * Find all objects that correspond to a given predicate
     *
     * @param matcher Predicate
     * @return Immutable collection of matching items
     */
    Collection<? extends T> findAllByQuery(Matcher<?, ? super T> matcher);

    /**
     * Find all objects that correspond to a predicate and sort using a specified sorter
     *
     * @param matcher predicate
     * @param sorter  Sorter
     * @return Immutable collection of matching items
     */
    default <Q> Collection<? extends T> findAllByQuerySorted(Matcher<?, ? super T> matcher,
        Sorter<T> sorter) {
        return sorter.sort(findAllByQuery(matcher));
    }

    /**
     * Save all items in a given collection
     *
     * @param collection Collection of items to save
     * @return A collection of the items that were successfully saved
     */
    Collection<? extends T> save(final Collection<? extends T> collection);

    /**
     * Delete all items in a given collection
     *
     * @param collection Collection of items that are to be deleted
     */
    void delete(Collection<T> collection);

    /**
     * Delete a single item
     *
     * @param item Item to be deleted
     */
    default void delete(T item) {
        delete(Collections.singleton(item));
    }

    /**
     * Find a single item that corresponds to a given identifier
     *
     * @param identifier Identifier
     * @return Item
     */
    Optional<? extends T> findSingle(ID identifier);

    /**
     * Find all items that correspond to a given identifier
     *
     * @param identifier Identifier
     * @return Items
     */
    Collection<? extends T> findAll(ID identifier);

}
