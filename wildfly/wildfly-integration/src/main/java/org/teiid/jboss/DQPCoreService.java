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

import jakarta.resource.spi.XATerminator;
import jakarta.resource.spi.work.WorkManager;
import jakarta.transaction.TransactionManager;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.teiid.PreParser;
import org.teiid.adminapi.impl.SessionMetadata;
import org.teiid.common.buffer.BufferManager;
import org.teiid.core.TeiidRuntimeException;
import org.teiid.deployers.CompositeVDB;
import org.teiid.deployers.VDBLifeCycleListener;
import org.teiid.deployers.VDBRepository;
import org.teiid.dqp.internal.datamgr.TranslatorRepository;
import org.teiid.dqp.internal.process.*;
import org.teiid.dqp.service.SessionService;
import org.teiid.dqp.service.TransactionService;
import org.teiid.logging.LogConstants;
import org.teiid.logging.LogManager;
import org.teiid.logging.MessageLevel;
import org.teiid.resource.spi.XAImporterImpl;
import org.teiid.runtime.jmx.JMXService;
import org.teiid.services.InternalEventDistributorFactory;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.function.Consumer;
import java.util.function.Supplier;


public class DQPCoreService extends DQPConfiguration implements Serializable, Service  {
    private static final long serialVersionUID = -4676205340262775388L;

    private transient TransactionServerImpl transactionServerImpl = new TransactionServerImpl();
    private transient DQPCore dqpCore = new DQPCore();
    private final Consumer<DQPCore> dqpConsumer;
    private transient JMXService jmx;

    private final Supplier<WorkManager> workManagerInjector;
    private final Supplier<XATerminator> xaTerminatorInjector;
    private final Supplier<TransactionManager> txnManagerInjector;
    private final Supplier<BufferManager> bufferManagerInjector;
    private final Supplier<TranslatorRepository> translatorRepositoryInjector;
    private final Supplier<VDBRepository> vdbRepositoryInjector;
    private final Supplier<AuthorizationValidator> authorizationValidatorInjector;
    private final Supplier<PreParser> preParserInjector;
    private final Supplier<SessionAwareCache<PreparedPlan>> preparedPlanCacheInjector;
    private final Supplier<SessionAwareCache<CachedResults>> resultSetCacheInjector;
    private final Supplier<InternalEventDistributorFactory> eventDistributorFactoryInjector;

    public DQPCoreService(Supplier<WorkManager> workManagerDep, Supplier<XATerminator> xatDep, Supplier<TransactionManager> tmDep, Supplier<BufferManager> bufmanDep, Supplier<TranslatorRepository> transRepDep, Supplier<VDBRepository> vdbRepDep, Supplier<AuthorizationValidator> authValdep, Supplier<PreParser> preParDep, Supplier<SessionAwareCache<CachedResults>> resultsetDep, Supplier<SessionAwareCache<PreparedPlan>> pplanDep, Supplier<InternalEventDistributorFactory> evtDistDep, Consumer<DQPCore> dqpConsumer) {
        this.workManagerInjector = workManagerDep;
        this.xaTerminatorInjector = xatDep;
        this.txnManagerInjector = tmDep;
        this.bufferManagerInjector = bufmanDep;
        this.translatorRepositoryInjector = transRepDep;
        this.vdbRepositoryInjector = vdbRepDep;
        this.authorizationValidatorInjector = authValdep;
        this.preParserInjector = preParDep;
        this.preparedPlanCacheInjector = pplanDep;
        this.resultSetCacheInjector = resultsetDep;
        this.eventDistributorFactoryInjector = evtDistDep;
        this.dqpConsumer = dqpConsumer;
    }

    @Override
    public void start(final StartContext context) {
        this.transactionServerImpl.setXaImporter(new XAImporterImpl(getXaTerminatorInjector().get(), getWorkManagerInjector().get()));
        this.transactionServerImpl.setTransactionManager(getTxnManagerInjector().get());
        this.transactionServerImpl.setDetectTransactions(true);
        setPreParser(preParserInjector.get());
        setAuthorizationValidator(authorizationValidatorInjector.get());
        this.dqpCore.setBufferManager(bufferManagerInjector.get());

        this.dqpCore.setTransactionService((TransactionService)LogManager.createLoggingProxy(LogConstants.CTX_TXN_LOG, transactionServerImpl, new Class[] {TransactionService.class}, MessageLevel.DETAIL, Thread.currentThread().getContextClassLoader()));
        this.dqpCore.setEventDistributor(getEventDistributorFactoryInjector().get().getReplicatedEventDistributor());
        this.dqpCore.setResultsetCache(getResultSetCacheInjector().get());
        this.dqpCore.setPreparedPlanCache(getPreparedPlanCacheInjector().get());
        this.dqpCore.start(this);

        final SessionService sessionService = (SessionService) context.getController().getServiceContainer().getService(TeiidServiceNames.SESSION).getValue();
        ServiceController<?> repo = context.getController().getServiceContainer().getRequiredService(TeiidServiceNames.BUFFER_MGR);
        this.jmx = new JMXService(this.dqpCore, BufferManagerService.class.cast(repo.getService()), sessionService);
        this.jmx.registerBeans();

        // add vdb life cycle listeners
        getVdbRepository().addListener(new VDBLifeCycleListener() {

            @Override
            public void removed(String name, CompositeVDB vdb) {
                // terminate all the previous sessions
                Collection<SessionMetadata> sessions = sessionService.getSessionsLoggedInToVDB(vdb.getVDBKey());
                for (SessionMetadata session:sessions) {
                    sessionService.terminateSession(session.getSessionId(), null);
                }

                // dump the caches.
                try {
                    SessionAwareCache<?> value = getResultSetCacheInjector().get();
                    if (value != null) {
                        value.clearForVDB(vdb.getVDBKey());
                    }
                    value = getPreparedPlanCacheInjector().get();
                    if (value != null) {
                        value.clearForVDB(vdb.getVDBKey());
                    }
                } catch (IllegalStateException e) {
                    //already shutdown
                }
            }

            @Override
            public void added(String name, CompositeVDB vdb) {
            }

            @Override
            public void finishedDeployment(String name, CompositeVDB cvdb) {
            }

            @Override
            public void beforeRemove(String name, CompositeVDB cvdb) {
            }
        });

        LogManager.logInfo(LogConstants.CTX_RUNTIME, IntegrationPlugin.Util.gs(IntegrationPlugin.Event.TEIID50001, this.dqpCore.getRuntimeVersion(), new Date(System.currentTimeMillis()).toString()));
        this.dqpConsumer.accept(dqpCore);
    }

    public DQPCore getValue() throws IllegalStateException, IllegalArgumentException {
        return this.dqpCore;
    }

    @Override
    public void stop(StopContext context) {
        try {
            this.dqpCore.stop();
        } catch(TeiidRuntimeException e) {
            // this bean is already shutdown
        }
        if (this.jmx != null) {
            jmx.unregisterBeans();
            jmx = null;
        }
        LogManager.logInfo(LogConstants.CTX_RUNTIME, IntegrationPlugin.Util.gs(IntegrationPlugin.Event.TEIID50002, new Date(System.currentTimeMillis()).toString()));
    }

    public Supplier<SessionAwareCache<CachedResults>> getResultSetCacheInjector() {
        return resultSetCacheInjector;
    }

    public Supplier<SessionAwareCache<PreparedPlan>> getPreparedPlanCacheInjector() {
        return preparedPlanCacheInjector;
    }

    public Supplier<TranslatorRepository> getTranslatorRepositoryInjector() {
        return translatorRepositoryInjector;
    }

    public Supplier<VDBRepository> getVdbRepositoryInjector() {
        return vdbRepositoryInjector;
    }

    private VDBRepository getVdbRepository() {
        return vdbRepositoryInjector.get();
    }

    public Supplier<AuthorizationValidator> getAuthorizationValidatorInjector() {
        return authorizationValidatorInjector;
    }

    public Supplier<PreParser> getPreParserInjector() {
        return preParserInjector;
    }

    public Supplier<BufferManager> getBufferManagerInjector() {
        return bufferManagerInjector;
    }

    public Supplier<TransactionManager> getTxnManagerInjector() {
        return txnManagerInjector;
    }

    public Supplier<XATerminator> getXaTerminatorInjector() {
        return xaTerminatorInjector;
    }

    public Supplier<WorkManager> getWorkManagerInjector() {
        return workManagerInjector;
    }

    public Supplier<InternalEventDistributorFactory> getEventDistributorFactoryInjector() {
        return eventDistributorFactoryInjector;
    }
}
