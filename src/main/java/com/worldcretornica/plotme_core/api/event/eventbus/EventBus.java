/*
 * This file contains code which has been inspired by or modified from
 * the following projects:
 *
 * Sponge, licensed under the MIT License (MIT).
 * Copyright (c) SpongePowered.org <http://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * WorldEdit, licensed under under the terms of the GNU Lesser General Public License version 3.
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * Contributors of this repository do not claim ownership of this code and all rights belong to the works
 * this is derived from.
 */

package com.worldcretornica.plotme_core.api.event.eventbus;

import com.worldcretornica.plotme_core.api.event.Event;
import com.worldcretornica.plotme_core.api.event.ICancellable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EventBus implements EventManager {

    private final Object lock = new Object();
    private final Map<Class<? extends Event>, Set<MethodEventHandler>> handlersByEvent = new ConcurrentHashMap<>();

    /**
     * A cache of all the handlers for an event type for quick event posting.
     *
     *
     * The cache is currently entirely invalidated if handlers are added or
     * removed.
     *
     */
    private final Map<Class<? extends Event>, HandlerCache> handlersCache = new ConcurrentHashMap<>();

    public EventBus() {
    }

    /**
     * Walk the supertype graph of {@code rootType} and collect every class /
     * interface in it. Replaces the Guava {@code TypeToken.of(rootType).getTypes().rawTypes()}
     * call. Order isn't significant — handlers are sorted by {@link Order} afterwards.
     */
    private static Set<Class<?>> collectAllSupertypes(Class<?> rootType) {
        Set<Class<?>> visited = new LinkedHashSet<>();
        collectAllSupertypes(rootType, visited);
        return visited;
    }

    private static void collectAllSupertypes(Class<?> type, Set<Class<?>> visited) {
        if (type == null || !visited.add(type)) {
            return;
        }
        collectAllSupertypes(type.getSuperclass(), visited);
        for (Class<?> iface : type.getInterfaces()) {
            collectAllSupertypes(iface, visited);
        }
    }

    private static boolean isValidHandler(Method method) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (!Modifier.isStatic(method.getModifiers()) && !Modifier.isAbstract(method.getModifiers())) {
            if (!Modifier.isInterface(method.getDeclaringClass().getModifiers()) && method.getReturnType().equals(void.class)) {
                if (paramTypes.length == 1 && Event.class.isAssignableFrom(paramTypes[0])) {
                    return true;
                }
            }
        }
        return false;
    }

    private HandlerCache bakeHandlers(Class<? extends Event> rootType) {
        final List<MethodEventHandler> registrations = new ArrayList<>();
        Set<Class<?>> types = collectAllSupertypes(rootType);

        synchronized (this.lock) {
            for (Class<?> type : types) {
                if (Event.class.isAssignableFrom(type)) {
                    Set<MethodEventHandler> handlers = this.handlersByEvent.get(type);
                    if (handlers != null) {
                        registrations.addAll(handlers);
                    }
                }
            }
        }

        Collections.sort(registrations);

        return new HandlerCache(registrations);
    }

    private HandlerCache getHandlerCache(Class<? extends Event> type) {
        return this.handlersCache.computeIfAbsent(type, this::bakeHandlers);
    }

    private List<Subscriber> findAllSubscribers(Object object) {
        List<Subscriber> subscribers = new ArrayList<>();
        Class<?> type = object.getClass();
        for (Method method : type.getMethods()) {
            Subscribe subscribe = method.getAnnotation(Subscribe.class);
            if (method.isAnnotationPresent(Subscribe.class)) {
                Class<?>[] paramTypes = method.getParameterTypes();

                if (isValidHandler(method)) {
                    //noinspection unchecked
                    Class<? extends Event> eventType = (Class<? extends Event>) paramTypes[0];
                    MethodEventHandler handler = new MethodEventHandler(subscribe.order(), object, method);
                    subscribers.add(new Subscriber(eventType, handler));
                } else {
                    //Just hope this doesn't happen. We can't log anything since the logger atm only works with Bukkit.
                }
            }
        }

        return subscribers;
    }

    public boolean register(Class<? extends Event> type, MethodEventHandler handler) {
        return register(new Subscriber(type, handler));
    }

    public boolean register(Subscriber subscriber) {
        List<Subscriber> single = new ArrayList<>();
        single.add(subscriber);
        return registerAll(single);
    }

    private boolean registerAll(List<Subscriber> subscribers) {
        synchronized (this.lock) {
            boolean changed = false;

            for (Subscriber sub : subscribers) {
                Set<MethodEventHandler> set = this.handlersByEvent
                        .computeIfAbsent(sub.getEventClass(), k -> new HashSet<>());
                if (set.add(sub.getHandler())) {
                    changed = true;
                }
            }

            if (changed) {
                this.handlersCache.clear();
            }

            return changed;
        }
    }

    public boolean unregister(Class<? extends Event> type, MethodEventHandler handler) {
        return unregister(new Subscriber(type, handler));
    }

    public boolean unregister(Subscriber subscriber) {
        List<Subscriber> single = new ArrayList<>();
        single.add(subscriber);
        return unregisterAll(single);
    }

    public boolean unregisterAll(List<Subscriber> subscribers) {
        synchronized (this.lock) {
            boolean changed = false;

            for (Subscriber sub : subscribers) {
                Set<MethodEventHandler> set = this.handlersByEvent.get(sub.getEventClass());
                if (set != null && set.remove(sub.getHandler())) {
                    changed = true;
                }
            }

            if (changed) {
                this.handlersCache.clear();
            }

            return changed;
        }
    }

    private void callListener(MethodEventHandler handler, Event event) {
        try {
            handler.handleEvent(event);
        } catch (Throwable t) {
            //Just hope this doesn't happen. We can't log anything since the logger atm only works with Bukkit.
        }
    }

    @Override
    public void register(Object object) {
        registerAll(findAllSubscribers(object));
    }

    @Override
    public void unregister(Object object) {
        unregisterAll(findAllSubscribers(object));
    }

    @Override
    public boolean post(Event event) {
        for (Order order : Order.values()) {
            for (MethodEventHandler handler : getHandlerCache(event.getClass()).getHandlersByOrder(order)) {
                callListener(handler, event);
            }
        }

        return event instanceof ICancellable && ((ICancellable) event).isCancelled();
    }

}
