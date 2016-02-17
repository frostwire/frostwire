package org.limewire.listener;

import java.awt.EventQueue;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.limewire.concurrent.ExecutorsHelper;
import org.limewire.concurrent.ThreadExecutor;
import org.limewire.util.ExceptionUtils;

import com.frostwire.logging.Logger;

/**
 * Maintains event listeners and broadcasts events to all listeners.
 * <p>
 * The annotations {@link SwingEDTEvent} and {@link BlockingEvent} can be added
 * to implementations of {@link EventListener#handleEvent(Object)} in order to 
 * allow those events to be dispatched on the EDT thread or a new thread.
 * <p>
 * If classes want to delegate implementations of {@link EventListener}, it is
 * important that the delegate listener's <code>handleEvent(E)</code> method
 * is called via {@link EventListenerList#dispatch(EventListener, Object, EventListenerListContext)}.  This
 * ensures that the event is dispatched appropriately, according to the 
 * annotation on the delegate listener.
 */
public class EventListenerList<E> implements ListenerSupport<E>, EventBroadcaster<E> {
    
    private final List<ListenerProxy<E>> listenerList = new CopyOnWriteArrayList<ListenerProxy<E>>();
    private final Logger log;
    private final EventListenerListContext context;

    /** Constructs an {@link EventListenerList} with a new context and no log. */
    public EventListenerList() {
        this(null, new EventListenerListContext());
    }
    
    /** Constructs an {@link EventListenerList} with a new context a log based on the given class. */
    @SuppressWarnings("rawtypes")
	public EventListenerList(Class loggerKey) {
        this(Logger.getLogger(loggerKey), new EventListenerListContext());
    }
    
    /** Constructs an {@link EventListenerList} with a new context the given log. */
    public EventListenerList(Logger log) {
        this(log, new EventListenerListContext());
    }
    
    /** Constructs an {@link EventListenerList} with the given context and no log. */
    public EventListenerList(EventListenerListContext context) {
        this(null, context);
    }
    
    /** Constructs an {@link EventListenerList} with the given context and log. */
    public EventListenerList(Logger log, EventListenerListContext context) {
        this.log = log;
        this.context = context;
    }
    
    /**
     * Dispatches the event to the listener. This scans the listener for
     * annotations and dispatches in the correct thread, according to the
     * annotation.
     * 
     * @param context the {@link EventListenerListContext} context to dispatch the
     *        event with. Usually retrieved by
     *        {@link EventListenerList#getContext()}. The context may be null,
     *        which will result in context not being used.
     */
    public static <E> void dispatch(EventListener<E> listener, E event, EventListenerListContext context) {
        EventListener<E> proxy = new ListenerProxy<E>(nonNull(listener, "listener"), context);
        proxy.handleEvent(event);
    }
    
    /**
     * Returns the context which can be used to dispatch events via
     * {@link EventListenerList#dispatch(EventListener, Object, org.limewire.listener.EventListenerList.EventListenerListContext)}.
     */
    public EventListenerListContext getContext() {
        return context; 
    }
    
    /** Adds the listener. */
    public void addListener(EventListener<E> listener) {
        listenerList.add(new ListenerProxy<E>(nonNull(listener, "listener"), context));
    }
    
    /** Returns true if the listener was removed. */
    public boolean removeListener(EventListener<E> listener) {
        nonNull(listener, "listener");
        
        // Find the proxy, then remove it.
        for(ListenerProxy<E> proxyListener : listenerList) {
            if(proxyListener.delegate.equals(listener)) {
                return listenerList.remove(proxyListener);
            }
        }
        return false;
    }
    
    /**
     * Notifies just the given listener about the given event.
     * This uses the {@link EventListenerListContext} of the current {@link EventListenerList}.
     * If you need to use another context, use {@link EventListenerList#dispatch(EventListener, Object, EventListenerListContext)}. 
     */
    public void dispatch(EventListener<E> listener, E event) {
        EventListenerList.dispatch(listener, event, context);
    }
    
    /** Broadcasts an event to all listeners. */
    public void broadcast(E event) {
        nonNull(event, "event");
        
        // When broadcasting, capture exceptions to make sure each listeners
        // gets a shot.  If an exception occurs and can be reported immediately,
        // it is reported.  Otherwise, is captured & the first exception
        // is reported after the loop finishes.
        
        Throwable t = null;
        for(ListenerProxy<E> listener : listenerList) {
            try {
                listener.handleEvent(event);
            } catch(Throwable thrown) {
                if(log != null) {
                    log.error("error dispatching " + event, thrown);
                }
                
                thrown = ExceptionUtils.reportOrReturn(thrown);
                if(thrown != null && t == null) {
                    t = thrown;
                }
            }
        }
        
        if(t != null) {
            ExceptionUtils.reportOrRethrow(t);
        }
    }
    
    /** Returns the size of the list. */
    public int size() {
        return listenerList.size();
    }

    /** Throws an exception with the given message if <code>t</code> is null. */
    private static <T> T nonNull(T t, String msg) {
        if (t == null)
            throw new NullPointerException("null: " + msg);
        return t;
    }
    
    private static final class ListenerProxy<E> implements EventListener<E> {
        private final EventListener<E> delegate;
        private final EventListenerListContext context;
        
        private volatile DispatchStrategy strategy = DispatchStrategy.UNKNOWN;
        
        public ListenerProxy(EventListener<E> delegate, EventListenerListContext context) {
            this.delegate = delegate;
            this.context = context;
        }
        
        @Override
        public void handleEvent(final E event) {
            // Note: This is not thread-safe, but it is OK to analyze multiple times.
            //       The internals of analyze make sure that only one ExecutorMap & Executor are
            //       ever set.
            if(strategy == DispatchStrategy.UNKNOWN) {
                strategy = analyze(delegate, event);                
            }
            
            strategy.dispatch(delegate, event);
        }
        
        /**
         * Loops through all 'handleEvent' methods whose parameter type can match event's
         * classes & superclasses.  When one is found, see if it is annotated with {@link SwingEDTEvent} or
         * {@link BlockingEvent}.
         */
        private DispatchStrategy analyze(EventListener<E> delegate, E event) {
            Class<?> eventClass = event.getClass();
            Method method = null;
            while(eventClass != null) {
                try {
                    method = delegate.getClass().getMethod("handleEvent", eventClass);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
                eventClass = eventClass.getSuperclass();
            }
            
            if(method == null) {
                throw new IllegalStateException("Unable to find method!");
            }

            BlockingEvent blockingEvent;
            if((blockingEvent = method.getAnnotation(BlockingEvent.class)) != null) {
                return DispatchStrategy.getBlockingStrategy(context, blockingEvent);
            } else {
                return DispatchStrategy.INLINE;
            }
        }
    }
    
    /** A strategy to dispatch events. */
    private static abstract class DispatchStrategy {
        /** Dispatches the event, possibly using the given executor. */
        abstract <E> void dispatch(EventListener<E> listener, E event);
        
        /** A strategy that always fails. */
        public static DispatchStrategy UNKNOWN = new DispatchStrategy() {
            @Override
            <E> void dispatch(EventListener<E> listener, E event) {
                throw new IllegalStateException("unknown dispatch!");
            }
        };

        /** A strategy that runs immediately. */
        public static DispatchStrategy INLINE = new DispatchStrategy() {
            @Override
            <E> void dispatch(EventListener<E> listener, E event) {
                listener.handleEvent(event);
            }
        };
        
        /** A strategy that dispatches on the Swing thread. */
        public static DispatchStrategy SWING = new DispatchStrategy() {
            @Override
            <E> void dispatch(final EventListener<E> listener, final E event) {
                if(EventQueue.isDispatchThread()) {
                    listener.handleEvent(event);
                } else {
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            listener.handleEvent(event);
                        }
                    });
                }
            }
        };
        
        /** A strategy that creates a new thread. */
        public static DispatchStrategy NEW_THREAD = new DispatchStrategy() {
            @Override
            <E> void dispatch(final EventListener<E> listener, final E event) {
                Runnable runner = new Runnable() {
                    public void run() {
                        listener.handleEvent(event);
                    }
                };
                ThreadExecutor.startThread(runner, "BlockingEvent");
            }
        };
        
        /** Returns the appropriate dispatch strategy for this event. */
        public static DispatchStrategy getBlockingStrategy(EventListenerListContext context, BlockingEvent event) {
            if(context == null) {
                return NEW_THREAD;
            } else {
                Executor executor = context.getOrCreateExecutor(event.queueName());
                if(executor == null) {
                    return NEW_THREAD;
                } else {
                    return new ExecutorDispatchStrategy(executor);
                }
            }
        };
    }
        
    /** A strategy that runs in the given executor. */
    private static class ExecutorDispatchStrategy extends DispatchStrategy {
        private final Executor executor;
        
        public ExecutorDispatchStrategy(Executor executor) {
            this.executor = executor;
        }
        
        @Override
        <E> void dispatch(final org.limewire.listener.EventListener<E> listener, final E event) {
            Runnable runner = new Runnable() {
                public void run() {
                    listener.handleEvent(event);
                }
            };
            executor.execute(runner);
        }
    }
    
    /** 
     * The context of an {@link EventListenerList}.  The context is used to ensure
     * that any state required over multiple event notifications within a single list
     * is maintained, such as notifying {@link BlockingEvent BlockingEvents} with a specific
     * {@link BlockingEvent#queueName()} in the same queue.
     */
    public static final class EventListenerListContext {
        // The use of AtomicReference here is to allow for cost-free instantiation
        // of a context.  Most contexts will not have any named executors,
        // so it would be wasteful to create a map to store them for every
        // EventListenerList.  In the event that a named executor is required,
        // the map will be created on-demand.
        private final AtomicReference<ConcurrentMap<String, Executor>> eventExecutorsRef = new AtomicReference<ConcurrentMap<String,Executor>>();
        
        private Executor getOrCreateExecutor(String queueName) {
            if(queueName != null && !queueName.equals("")) {
                ConcurrentMap<String, Executor> executorMap = eventExecutorsRef.get();
                // If no executorMap exists already, create a new one.
                if(executorMap == null) {
                    eventExecutorsRef.compareAndSet(null, new ConcurrentHashMap<String, Executor>());
                    executorMap = eventExecutorsRef.get();
                }
                
                // If no executor exists for this queueName, create a new one.
                Executor executor = executorMap.get(queueName);
                if(executor == null) {
                    executorMap.putIfAbsent(queueName, ExecutorsHelper.newProcessingQueue("BlockingEventQueue-" + queueName));
                    executor = executorMap.get(queueName);
                }
                return executor;
            } else {
                return null; // No executor required.
            }
        }
    }
}
