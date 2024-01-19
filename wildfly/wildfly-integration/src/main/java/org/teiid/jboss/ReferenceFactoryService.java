/*
 * Copyright Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags and
 * the COPYRIGHT.txt file distributed with this work.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.teiid.jboss;

import org.jboss.as.naming.ContextListAndJndiViewManagedReferenceFactory;
import org.jboss.as.naming.ContextListManagedReferenceFactory;
import org.jboss.as.naming.ManagedReference;
import org.jboss.as.naming.ValueManagedReference;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

import java.util.function.Consumer;
import java.util.function.Supplier;


class ReferenceFactoryService<T> implements Service, ContextListAndJndiViewManagedReferenceFactory {
    private final Supplier<T> injector;

    private ManagedReference reference;
    private Consumer<ManagedReference> referenceConsumer;

    public ReferenceFactoryService(Supplier<T> suppliedValue, Consumer<ManagedReference> outgoingReference) {
        this.injector = suppliedValue;
        this.referenceConsumer = outgoingReference;
    }

    public synchronized void start(StartContext startContext) throws StartException {
        reference = new ValueManagedReference(injector.get());
        referenceConsumer.accept(reference);
    }

    public synchronized void stop(StopContext stopContext) {
        reference = null;
    }

    public synchronized ManagedReference getReference() {
        return reference;
    }

    @Override
    public String getInstanceClassName() {
        final Object value = reference != null ? reference.getInstance() : null;
        return value != null ? value.getClass().getName() : ContextListManagedReferenceFactory.DEFAULT_INSTANCE_CLASS_NAME;
    }

    @Override
    public String getJndiViewInstanceValue() {
        final Object value = reference != null ? reference.getInstance() : null;
        return String.valueOf(value);
    }
}
