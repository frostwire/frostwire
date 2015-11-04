package org.limewire.listener;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to mark an event as needing to be run on a new thread.
 * If queueName is specified, then all events specifying that
 * queueName within a specific {@link EventListenerList} will
 * be run in the same single-threaded {@link Executor}.
 * 
 * This allows events to be run in a separate thread than
 * the thread generating the events, yet still be queued
 * to run one after the other.
 */
@Target(value = { ElementType.METHOD })
@Retention(value = RetentionPolicy.RUNTIME)
@Documented
public @interface BlockingEvent {

    String queueName() default "";

}
