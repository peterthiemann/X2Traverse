/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package de.unifr.acp.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;


public class WeakIdentityHashMap<K,V> extends AbstractMap<K,V> implements Map<K,V> {

    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    private static final int MAXIMUM_CAPACITY = 1 << 30;

    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    Entry<K,V>[] table;

    private int size;

    private int threshold;

    private final float loadFactor;

    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    int modCount;

        @SuppressWarnings({ "unchecked", "cast" })
        private Entry<K, V>[] newTable(int n) {
                return (Entry<K, V>[]) new Entry[n];
        }

    public WeakIdentityHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal Initial Capacity: " + initialCapacity);
                if (initialCapacity > MAXIMUM_CAPACITY)
                        initialCapacity = MAXIMUM_CAPACITY;

                if (loadFactor <= 0 || Float.isNaN(loadFactor))
                        throw new IllegalArgumentException("Illegal Load factor: " + loadFactor);
                int capacity = 1;
                while (capacity < initialCapacity)
                        capacity <<= 1;
                table = newTable(capacity);
                this.loadFactor = loadFactor;
                threshold = (int) (capacity * loadFactor);
    }

        public WeakIdentityHashMap(int initialCapacity) {
                this(initialCapacity, DEFAULT_LOAD_FACTOR);
        }

        public WeakIdentityHashMap() {
                this.loadFactor = DEFAULT_LOAD_FACTOR;
                threshold = DEFAULT_INITIAL_CAPACITY;
                table = newTable(DEFAULT_INITIAL_CAPACITY);
        }

        public WeakIdentityHashMap(Map<? extends K, ? extends V> m) {
                this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1, 16),
                                DEFAULT_LOAD_FACTOR);
                putAll(m);
        }

        private static final Object NULL_KEY = new Object();

        private static Object maskNull(Object key) {
                return (key == null) ? NULL_KEY : key;
        }

        static Object unmaskNull(Object key) {
                return (key == NULL_KEY) ? null : key;
        }

        private static boolean eq(Object x, Object y) {
                // MMSH return x == y || x.equals(y);
                return x == y;
        }

        private static int indexFor(int h, int length) {
                return h & (length - 1);
        }

        private void expungeStaleEntries() {
                for (Object x; (x = queue.poll()) != null;) {
                        synchronized (queue) {
                                @SuppressWarnings("unchecked")
                                Entry<K, V> e = (Entry<K, V>) x;
                                int i = indexFor(e.hash, table.length);

                                Entry<K, V> prev = table[i];
                                Entry<K, V> p = prev;
                                while (p != null) {
                                        Entry<K, V> next = p.next;
                                        if (p == e) {
                                                if (prev == e)
                                                        table[i] = next;
                                                else
                                                        prev.next = next;
                                                // Must not null out e.next;
                                                // stale entries may be in use by a HashIterator
                                                e.value = null; // Help GC
                                                size--;
                                                break;
                                        }
                                        prev = p;
                                        p = next;
                                }
                        }
                }
        }

        private Entry<K, V>[] getTable() {
                expungeStaleEntries();
                return table;
        }

        public int size() {
                if (size == 0)
                        return 0;
                expungeStaleEntries();
                return size;
        }

        public boolean isEmpty() {
                return size() == 0;
        }

        public V get(Object key) {
                Object k = maskNull(key);
                // MMSH int h = MyHashMap.hash(k.hashCode());
                int h = HashMap.hash(System.identityHashCode(k));
                
                Entry<K, V>[] tab = getTable();
                int index = indexFor(h, tab.length);
                Entry<K, V> e = tab[index];
                while (e != null) {
                        if (e.hash == h && eq(k, e.get())) {
                                return e.value;
                        }
                        e = e.next;
                }
                return null;
        }

    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    Entry<K,V> getEntry(Object key) {
        Object k = maskNull(key);
        //MMSH int h = MyHashMap.hash(k.hashCode());
        int h = HashMap.hash(System.identityHashCode(k));
        Entry<K,V>[] tab = getTable();
        int index = indexFor(h, tab.length);
        Entry<K,V> e = tab[index];
        while (e != null && !(e.hash == h && eq(k, e.get())))
            e = e.next;
        return e;
    }

    public V put(K key, V value) {
        Object k = maskNull(key);
        //MMSH int h = MyHashMap.hash(k.hashCode());
        int h = HashMap.hash(System.identityHashCode(k));
                
        Entry<K,V>[] tab = getTable();
        int i = indexFor(h, tab.length);

        for (Entry<K,V> e = tab[i]; e != null; e = e.next) {
            if (h == e.hash && eq(k, e.get())) {
                V oldValue = e.value;
                if (value != oldValue)
                    e.value = value;
                return oldValue;
            }
        }

        modCount++;
        Entry<K,V> e = tab[i];
        tab[i] = new Entry<>(k, value, queue, h, e);
        if (++size >= threshold)
            resize(tab.length * 2);
        return null;
    }

    void resize(int newCapacity) {
        Entry<K,V>[] oldTable = getTable();
        int oldCapacity = oldTable.length;
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }

        Entry<K,V>[] newTable = newTable(newCapacity);
        transfer(oldTable, newTable);
        table = newTable;

        /*
         * If ignoring null elements and processing ref queue caused massive
         * shrinkage, then restore old table.  This should be rare, but avoids
         * unbounded expansion of garbage-filled tables.
         */
        if (size >= threshold / 2) {
            threshold = (int)(newCapacity * loadFactor);
        } else {
            expungeStaleEntries();
            transfer(newTable, oldTable);
            table = oldTable;
        }
    }

    private void transfer(Entry<K,V>[] src, Entry<K,V>[] dest) {
        for (int j = 0; j < src.length; ++j) {
            Entry<K,V> e = src[j];
            src[j] = null;
            while (e != null) {
                Entry<K,V> next = e.next;
                Object key = e.get();
                if (key == null) {
                    e.next = null;  // Help GC
                    e.value = null; //  "   "
                    size--;
                } else {
                    int i = indexFor(e.hash, dest.length);
                    e.next = dest[i];
                    dest[i] = e;
                }
                e = next;
            }
        }
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        int numKeysToBeAdded = m.size();
        
        if (numKeysToBeAdded == 0)
            return;

        if (numKeysToBeAdded > threshold) {
            int targetCapacity = (int)(numKeysToBeAdded / loadFactor + 1);
            if (targetCapacity > MAXIMUM_CAPACITY)
                targetCapacity = MAXIMUM_CAPACITY;
            int newCapacity = table.length;
            while (newCapacity < targetCapacity)
                newCapacity <<= 1;
            if (newCapacity > table.length)
                resize(newCapacity);
        }

        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            put(e.getKey(), e.getValue());
    }

    public V remove(Object key) {
        Object k = maskNull(key);
        //MMSH int h = MyHashMap.hash(k.hashCode());
        int h = HashMap.hash(System.identityHashCode(k));
        Entry<K,V>[] tab = getTable();
        int i = indexFor(h, tab.length);
        Entry<K,V> prev = tab[i];
        Entry<K,V> e = prev;

        while (e != null) {
            Entry<K,V> next = e.next;
            if (h == e.hash && eq(k, e.get())) {
                modCount++;
                size--;
                if (prev == e)
                    tab[i] = next;
                else
                    prev.next = next;
                return e.value;
            }
            prev = e;
            e = next;
        }

        return null;
    }

    boolean removeMapping(Object o) {
        if (!(o instanceof Map.Entry))
            return false;
        Entry<K,V>[] tab = getTable();
        Map.Entry<?,?> entry = (Map.Entry<?,?>)o;
        Object k = maskNull(entry.getKey());
        //MMSH int h = MyHashMap.hash(k.hashCode());
        int h = HashMap.hash(System.identityHashCode(k));
        int i = indexFor(h, tab.length);
        Entry<K,V> prev = tab[i];
        Entry<K,V> e = prev;

        while (e != null) {
            Entry<K,V> next = e.next;
            if (h == e.hash && e.equals(entry)) {
                modCount++;
                size--;
                if (prev == e)
                    tab[i] = next;
                else
                    prev.next = next;
                return true;
            }
            prev = e;
            e = next;
        }

        return false;
    }

    public void clear() {
        while (queue.poll() != null) {
                }

        modCount++;
        Arrays.fill(table, null);
        size = 0;

        //TODO Was   while (queue.poll() != null); before. Check if it has no problem
        while (queue.poll() != null) {
        }
    }

    public boolean containsValue(Object value) {
        if (value==null)
            return containsNullValue();

        Entry<K,V>[] tab = getTable();
        for (int i = tab.length; i-- > 0;)
            for (Entry<K,V> e = tab[i]; e != null; e = e.next)
                if (value.equals(e.value))
                    return true;
        return false;
    }

    private boolean containsNullValue() {
        Entry<K,V>[] tab = getTable();
        for (int i = tab.length; i-- > 0;)
            for (Entry<K,V> e = tab[i]; e != null; e = e.next)
                if (e.value==null)
                    return true;
        return false;
    }

    private static class Entry<K,V> extends WeakReference<Object> implements Map.Entry<K,V> {
        V value;
        final int hash;
        Entry<K,V> next;

        Entry(Object key, V value, ReferenceQueue<Object> queue, int hash, Entry<K,V> next) {
            super(key, queue);
            this.value = value;
            this.hash  = hash;
            this.next  = next;
        }

        @SuppressWarnings("unchecked")
        public K getKey() {
            return (K) WeakIdentityHashMap.unmaskNull(get());
        }

        public V getValue() {
            return value;
        }

        public V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        public boolean equals(Object o) {
                        if (!(o instanceof Map.Entry))
                                return false;
                        Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                        K k1 = getKey();
                        Object k2 = e.getKey();
                        if (k1 == k2 || (k1 != null && k1.equals(k2))) {
                                V v1 = getValue();
                                Object v2 = e.getValue();
                                if (v1 == v2 || (v1 != null && v1.equals(v2)))
                                        return true;
                        }
                        return false;
        }

        public int hashCode() {
                        K k = getKey();
                        V v = getValue();
                        return ((k == null ? 0 : k.hashCode()) ^ (v == null ? 0 : v.hashCode()));
        }

        public String toString() {
            return getKey() + "=" + getValue();
        }
    }

    private abstract class HashIterator<T> implements Iterator<T> {
        private int index;
        private Entry<K,V> entry = null;
        private Entry<K,V> lastReturned = null;
        private int expectedModCount = modCount;

        /**
         * Strong reference needed to avoid disappearance of key
         * between hasNext and next
         */
        private Object nextKey = null;

        /**
         * Strong reference needed to avoid disappearance of key
         * between nextEntry() and any use of the entry
         */
        private Object currentKey = null;

        HashIterator() {
            index = isEmpty() ? 0 : table.length;
        }

        public boolean hasNext() {
            Entry<K,V>[] t = table;

            while (nextKey == null) {
                Entry<K,V> e = entry;
                int i = index;
                while (e == null && i > 0)
                    e = t[--i];
                entry = e;
                index = i;
                if (e == null) {
                    currentKey = null;
                    return false;
                }
                nextKey = e.get(); // hold on to key in strong ref
                if (nextKey == null)
                    entry = entry.next;
            }
            return true;
        }

        /** The common parts of next() across different types of iterators */
        protected Entry<K,V> nextEntry() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            if (nextKey == null && !hasNext())
                throw new NoSuchElementException();

            lastReturned = entry;
            entry = entry.next;
            currentKey = nextKey;
            nextKey = null;
            return lastReturned;
        }

        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();

            WeakIdentityHashMap.this.remove(currentKey);
            expectedModCount = modCount;
            lastReturned = null;
            currentKey = null;
        }

    }

    private class ValueIterator extends HashIterator<V> {
        public V next() {
            return nextEntry().value;
        }
    }

    private class KeyIterator extends HashIterator<K> {
        public K next() {
            return nextEntry().getKey();
        }
    }

    private class EntryIterator extends HashIterator<Map.Entry<K,V>> {
        public Map.Entry<K,V> next() {
            return nextEntry();
        }
    }

    // Views

    private transient Set<Map.Entry<K,V>> entrySet = null;

    public Set<K> keySet() {
        Set<K> ks = keySet;
        return (ks != null ? ks : (keySet = new KeySet()));
    }

    private class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return new KeyIterator();
        }

        public int size() {
            return WeakIdentityHashMap.this.size();
        }

        public boolean contains(Object o) {
            return containsKey(o);
        }

        public boolean remove(Object o) {
            if (containsKey(o)) {
                WeakIdentityHashMap.this.remove(o);
                return true;
            }
            else
                return false;
        }

        public void clear() {
            WeakIdentityHashMap.this.clear();
        }
    }

    public Collection<V> values() {
        Collection<V> vs = values;
        return (vs != null) ? vs : (values = new Values());
    }

    private class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return new ValueIterator();
        }

        public int size() {
            return WeakIdentityHashMap.this.size();
        }

        public boolean contains(Object o) {
            return containsValue(o);
        }

        public void clear() {
                WeakIdentityHashMap.this.clear();
        }
    }

    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es = entrySet;
        return es != null ? es : (entrySet = new EntrySet());
    }

    private class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }

        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            Entry<K,V> candidate = getEntry(e.getKey());
            return candidate != null && candidate.equals(e);
        }

        public boolean remove(Object o) {
            return removeMapping(o);
        }

        public int size() {
            return WeakIdentityHashMap.this.size();
        }

        public void clear() {
                WeakIdentityHashMap.this.clear();
        }

        private List<Map.Entry<K,V>> deepCopy() {
            List<Map.Entry<K,V>> list = new ArrayList<>(size());
            for (Map.Entry<K,V> e : this)
                list.add(new AbstractMap.SimpleEntry<>(e));
            return list;
        }

        public Object[] toArray() {
            return deepCopy().toArray();
        }

        public <T> T[] toArray(T[] a) {
            return deepCopy().toArray(a);
        }
    }
}
