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

import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.teiid.deployers.VDBRepository;
import org.teiid.dqp.internal.process.DQPCore;
import org.teiid.runtime.MaterializationManager;

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

class MaterializationManagementService implements Service {
    private ScheduledExecutorService scheduler;
    private MaterializationManager manager;
    private final Consumer<MaterializationManager> managerCons;
    protected final Supplier<DQPCore> dqpInjector;
    protected final Supplier<VDBRepository> vdbRepositoryInjector;
    protected final Supplier<NodeTracker> nodeTrackerInjector;
    private JBossLifeCycleListener shutdownListener;

    public MaterializationManagementService(JBossLifeCycleListener shutdownListener, ScheduledExecutorService scheduler, Supplier<DQPCore> dqpCoreDep, Supplier<VDBRepository> vdbRepDep, Supplier<NodeTracker> nodeTrackerDep, Consumer<MaterializationManager> managerCons) {
        this.shutdownListener = shutdownListener;
        this.scheduler = scheduler;
        this.dqpInjector = dqpCoreDep;
        this.vdbRepositoryInjector = vdbRepDep;
        this.nodeTrackerInjector = nodeTrackerDep;
        this.managerCons = managerCons;
    }

    @Override
    public void start(StartContext context) throws StartException {
        manager = new MaterializationManager(shutdownListener) {
            @Override
            public ScheduledExecutorService getScheduledExecutorService() {
                return scheduler;
            }

            @Override
            public DQPCore getDQP() {
                return dqpInjector.get();
            }

            @Override
            public VDBRepository getVDBRepository() {
                return vdbRepositoryInjector.get();
            }
        };

        vdbRepositoryInjector.get().addListener(manager);

        if (nodeTrackerInjector.get() != null) {
            nodeTrackerInjector.get().addNodeListener(manager);
        }
        this.managerCons.accept(manager);
    }

    @Override
    public void stop(StopContext context) {
        scheduler.shutdownNow();
        vdbRepositoryInjector.get().removeListener(manager);
        NodeTracker value = nodeTrackerInjector.get();
        if (value != null) {
            value.removeNodeListener(manager);
        }
    }
}
