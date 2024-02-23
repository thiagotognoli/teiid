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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;
import static org.teiid.jboss.TeiidConstants.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.resource.spi.XATerminator;
import jakarta.resource.spi.work.WorkManager;
import jakarta.transaction.TransactionManager;

import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.as.controller.*;
import org.jboss.as.controller.access.Environment;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.RelativePathService;
import org.jboss.as.naming.ManagedReference;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.ServiceBasedNamingStore;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.naming.service.BinderService;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.Services;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.moduleservice.ServiceModuleLoader;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.teiid.CommandContext;
import org.teiid.PolicyDecider;
import org.teiid.PreParser;
import org.teiid.adminapi.impl.VDBTranslatorMetaData;
import org.teiid.cache.CacheFactory;
import org.teiid.common.buffer.BufferManager;
import org.teiid.common.buffer.TupleBufferCache;
import org.teiid.core.util.NamedThreadFactory;
import org.teiid.deployers.RestWarGenerator;
import org.teiid.deployers.TranslatorUtil;
import org.teiid.deployers.VDBRepository;
import org.teiid.deployers.VDBStatusChecker;
import org.teiid.dqp.internal.datamgr.ConnectorManagerRepository;
import org.teiid.dqp.internal.datamgr.ConnectorManagerRepository.ConnectorManagerException;
import org.teiid.dqp.internal.datamgr.TranslatorRepository;
import org.teiid.dqp.internal.process.*;
import org.teiid.dqp.service.SessionService;
import org.teiid.events.EventDistributorFactory;
import org.teiid.logging.LogConstants;
import org.teiid.logging.LogManager;
import org.teiid.net.socket.AuthenticationType;
import org.teiid.query.ObjectReplicator;
import org.teiid.query.function.SystemFunctionManager;
import org.teiid.query.metadata.SystemMetadata;
import org.teiid.replication.jgroups.JGroupsObjectReplicator;
import org.teiid.runtime.MaterializationManager;
import org.teiid.services.InternalEventDistributorFactory;
import org.teiid.services.SessionServiceImpl;
import org.teiid.translator.ExecutionFactory;
import org.wildfly.clustering.infinispan.service.InfinispanCacheRequirement;
import org.wildfly.clustering.infinispan.service.InfinispanRequirement;
import org.wildfly.clustering.jgroups.ChannelFactory;
import org.wildfly.clustering.jgroups.spi.JGroupsRequirement;

class TeiidAdd extends AbstractAddStepHandler {

    public static TeiidAdd INSTANCE = new TeiidAdd();

    static SimpleAttributeDefinition[] ATTRIBUTES = {
        TeiidConstants.ALLOW_ENV_FUNCTION_ELEMENT,
        TeiidConstants.THREAD_COUNT_ATTRIBUTE,
        TeiidConstants.MAX_THREADS_ELEMENT,
        TeiidConstants.MAX_ACTIVE_PLANS_ELEMENT,
        TeiidConstants.USER_REQUEST_SOURCE_CONCURRENCY_ELEMENT,
        TeiidConstants.TIME_SLICE_IN_MILLI_ELEMENT,
        TeiidConstants.MAX_ROWS_FETCH_SIZE_ELEMENT,
        TeiidConstants.LOB_CHUNK_SIZE_IN_KB_ELEMENT,
        TeiidConstants.QUERY_THRESHOLD_IN_SECS_ELEMENT,
        TeiidConstants.MAX_SOURCE_ROWS_ELEMENT,
        TeiidConstants.EXCEPTION_ON_MAX_SOURCE_ROWS_ELEMENT,
        TeiidConstants.DETECTING_CHANGE_EVENTS_ELEMENT,
        TeiidConstants.QUERY_TIMEOUT,
        TeiidConstants.WORKMANAGER,
        TeiidConstants.PREPARSER_MODULE_ELEMENT,
        TeiidConstants.AUTHORIZATION_VALIDATOR_MODULE_ELEMENT,
        TeiidConstants.POLICY_DECIDER_MODULE_ELEMENT,
        TeiidConstants.DATA_ROLES_REQUIRED_ELEMENT,

        // object replicator
        TeiidConstants.DC_STACK_ATTRIBUTE,

        // Buffer Service
        TeiidConstants.USE_DISK_ATTRIBUTE,
        TeiidConstants.INLINE_LOBS,
        TeiidConstants.PROCESSOR_BATCH_SIZE_ATTRIBUTE,
        TeiidConstants.MAX_PROCESSING_KB_ATTRIBUTE,
        TeiidConstants.MAX_RESERVED_KB_ATTRIBUTE,
        TeiidConstants.MAX_FILE_SIZE_ATTRIBUTE,
        TeiidConstants.MAX_BUFFER_SPACE_ATTRIBUTE,
        TeiidConstants.MAX_OPEN_FILES_ATTRIBUTE,
        TeiidConstants.MEMORY_BUFFER_SPACE_ATTRIBUTE,
        TeiidConstants.MEMORY_BUFFER_OFFHEAP_ATTRIBUTE,
        TeiidConstants.MAX_STORAGE_OBJECT_SIZE_ATTRIBUTE,
        TeiidConstants.ENCRYPT_FILES_ATTRIBUTE,

        // Buffer Manager
        TeiidConstants.BUFFER_MANAGER_USE_DISK_ATTRIBUTE,
        TeiidConstants.BUFFER_MANAGER_INLINE_LOBS,
        TeiidConstants.BUFFER_MANAGER_PROCESSOR_BATCH_SIZE_ATTRIBUTE,
        TeiidConstants.BUFFER_MANAGER_MAX_PROCESSING_KB_ATTRIBUTE,
        TeiidConstants.BUFFER_MANAGER_MAX_RESERVED_MB_ATTRIBUTE,
        TeiidConstants.BUFFER_MANAGER_MAX_FILE_SIZE_ATTRIBUTE,
        TeiidConstants.BUFFER_MANAGER_MAX_BUFFER_SPACE_ATTRIBUTE,
        TeiidConstants.BUFFER_MANAGER_MAX_OPEN_FILES_ATTRIBUTE,
        TeiidConstants.BUFFER_MANAGER_MEMORY_BUFFER_SPACE_ATTRIBUTE,
        TeiidConstants.BUFFER_MANAGER_MEMORY_BUFFER_OFFHEAP_ATTRIBUTE,
        TeiidConstants.BUFFER_MANAGER_MAX_STORAGE_OBJECT_SIZE_ATTRIBUTE,
        TeiidConstants.BUFFER_MANAGER_ENCRYPT_FILES_ATTRIBUTE,

        // prepared plan cache
        TeiidConstants.PPC_NAME_ATTRIBUTE,
        TeiidConstants.PPC_CONTAINER_NAME_ATTRIBUTE,
        TeiidConstants.PPC_ENABLE_ATTRIBUTE,

        // resultset cache
        TeiidConstants.RSC_NAME_ATTRIBUTE,
        TeiidConstants.RSC_CONTAINER_NAME_ATTRIBUTE,
        TeiidConstants.RSC_MAX_STALENESS_ATTRIBUTE,
        TeiidConstants.RSC_ENABLE_ATTRIBUTE,

        // session
        TeiidConstants.AUTHENTICATION_SECURITY_DOMAIN_ATTRIBUTE,
        TeiidConstants.AUTHENTICATION_MAX_SESSIONS_ALLOWED_ATTRIBUTE,
        TeiidConstants.AUTHENTICATION_SESSION_EXPIRATION_TIME_LIMIT_ATTRIBUTE,
        TeiidConstants.AUTHENTICATION_TYPE_ATTRIBUTE,
        TeiidConstants.AUTHENTICATION_TRUST_ALL_LOCAL_ATTRIBUTE
    };

    @Override
    protected void populateModel(final OperationContext context,
            final ModelNode operation, final Resource resource)
            throws OperationFailedException {
        resource.getModel().setEmptyObject();
        populate(operation, resource.getModel());

        if (context.getProcessType().equals(ProcessType.STANDALONE_SERVER) && context.isNormalServer()) {
            deployResources(context);
        }
    }

    @Override
    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
        throw new UnsupportedOperationException();
    }

    static void populate(ModelNode operation, ModelNode model) throws OperationFailedException {
        for (int i = 0; i < ATTRIBUTES.length; i++) {
            ATTRIBUTES[i].validateAndSet(operation, model);
        }
    }

    @Override
    protected void performRuntime(final OperationContext context,
            final ModelNode operation, final ModelNode model)
            throws OperationFailedException {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try {
            try {
                classloader = Module.getCallerModule().getClassLoader();
            } catch(Throwable t) {
                //ignore..
            }
            Thread.currentThread().setContextClassLoader(classloader);
            initilaizeTeiidEngine(context, operation);
        } finally {
            Thread.currentThread().setContextClassLoader(classloader);
        }
    }

    public String getNodeName() {
        String nodeName = System.getProperty("jboss.node.name");
        try {
            return (nodeName != null)?nodeName:InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    private void initilaizeTeiidEngine(final OperationContext context,
            final ModelNode operation) throws OperationFailedException {
        ServiceTarget target = context.getCapabilityServiceTarget();

        final String nodeName = getNodeName();

        Environment environment = context.getCallEnvironment();
        final JBossLifeCycleListener shutdownListener = new JBossLifeCycleListener(environment);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Teiid Timer")); //$NON-NLS-1$

        // async thread-pool
        int maxThreads = 10;
        if(asInt(THREAD_COUNT_ATTRIBUTE, operation, context) != null) {
            maxThreads = asInt(THREAD_COUNT_ATTRIBUTE, operation, context);
        }
        buildThreadService(maxThreads, target);

        // translator repository
        ServiceBuilder<?> trService = target.addService(TeiidServiceNames.TRANSLATOR_REPO);
        Consumer<TranslatorRepository> repoProvider = trService.provides(TeiidServiceNames.TRANSLATOR_REPO);
        final TranslatorRepository translatorRepo = new TranslatorRepository();
        Service rpService = Service.newInstance(repoProvider, translatorRepo);
        trService.setInstance(rpService);
        trService.install();

        final ConnectorManagerRepository connectorManagerRepo = buildConnectorManagerRepository(translatorRepo);

        // system function tree
        SystemFunctionManager systemFunctionManager = SystemMetadata.getInstance().getSystemFunctionManager();

        // VDB repository
        final VDBRepository vdbRepository = new VDBRepository();
        vdbRepository.setSystemFunctionManager(systemFunctionManager);
        if (isDefined(DATA_ROLES_REQUIRED_ELEMENT, operation, context) && asBoolean(DATA_ROLES_REQUIRED_ELEMENT, operation, context)) {
            vdbRepository.setDataRolesRequired(true);
        }

        if (isDefined(ALLOW_ENV_FUNCTION_ELEMENT, operation, context)) {
            vdbRepository.setAllowEnvFunction(asBoolean(ALLOW_ENV_FUNCTION_ELEMENT, operation, context));
        }
        else {
            vdbRepository.setAllowEnvFunction(false);
        }

        // VDB Status manager
        ServiceBuilder<?> statusBuilder = target.addService(TeiidServiceNames.VDB_STATUS_CHECKER);
        Consumer<VDBStatusChecker> statusCheckerConsumer = statusBuilder.provides(TeiidServiceNames.VDB_STATUS_CHECKER);
        Supplier<Executor> executorDep = statusBuilder.requires(TeiidServiceNames.THREAD_POOL_SERVICE);
        Supplier<VDBRepository> vdbRepoDep = statusBuilder.requires(TeiidServiceNames.VDB_REPO);
        final VDBStatusCheckerExecutorService statusChecker = new VDBStatusCheckerExecutorService(executorDep, vdbRepoDep);
        statusBuilder.setInstance(Service.newInstance(statusCheckerConsumer, statusChecker));
        statusBuilder.install();

        RelativePathService.addService(TeiidServiceNames.DATA_DIR, "teiid-data", "jboss.server.data.dir", target); //$NON-NLS-1$ //$NON-NLS-2$
        ServiceBuilder<?> objectSerializerService = target.addService(TeiidServiceNames.OBJECT_SERIALIZER);
        Consumer<ObjectSerializer> osCons = objectSerializerService.provides(TeiidServiceNames.OBJECT_SERIALIZER);
        Supplier<String> pathDep = objectSerializerService.requires(TeiidServiceNames.DATA_DIR);
        final ObjectsSerializerService serializer = new ObjectsSerializerService(pathDep, osCons);
        objectSerializerService.setInstance(serializer);
        objectSerializerService.install();

        // Object Replicator
        boolean replicatorAvailable = false;
        ServiceController<?> objectReplicatorController = null;
        if (isDefined(DC_STACK_ATTRIBUTE, operation, context)) {
            String stack = asString(DC_STACK_ATTRIBUTE, operation, context);

            replicatorAvailable = true;
            ServiceBuilder<?> serviceBuilder = target.addService(TeiidServiceNames.OBJECT_REPLICATOR);
            Consumer<JGroupsObjectReplicator> replicatorInstance = serviceBuilder.provides(TeiidServiceNames.OBJECT_REPLICATOR);
            Supplier<ChannelFactory> channelFactorySupplier = serviceBuilder.requires(JGroupsRequirement.CHANNEL_FACTORY.getServiceName(context, stack));//$NON-NLS-1$ //$NON-NLS-2$
            Supplier<Executor> poolServiceSupplier = serviceBuilder.requires(TeiidServiceNames.THREAD_POOL_SERVICE);
            JGroupsObjectReplicatorService replicatorService = new JGroupsObjectReplicatorService(channelFactorySupplier, poolServiceSupplier, replicatorInstance);
            serviceBuilder.setInstance(replicatorService);
            objectReplicatorController = serviceBuilder.install();
            LogManager.logInfo(LogConstants.CTX_RUNTIME, IntegrationPlugin.Util.gs(IntegrationPlugin.Event.TEIID50003));

            ServiceBuilder<?> nodeTrackerBuilder = target.addService(TeiidServiceNames.NODE_TRACKER_SERVICE);
            Supplier<ChannelFactory> channelFactoryDep = nodeTrackerBuilder.requires(JGroupsRequirement.CHANNEL_FACTORY.getServiceName(context, stack));//$NON-NLS-1$ //$NON-NLS-2$
            NodeTrackerService trackerService = new NodeTrackerService(nodeName, scheduler, channelFactoryDep);
            nodeTrackerBuilder.setInstance(trackerService);
            nodeTrackerBuilder.install();
        } else {
            LogManager.logDetail(LogConstants.CTX_RUNTIME, IntegrationPlugin.Util.getString("distributed_cache_not_enabled")); //$NON-NLS-1$
        }

        ServiceBuilder<?> vdbRepoService = target.addService(TeiidServiceNames.VDB_REPO);
        Supplier<BufferManager> bufferManagerDep = vdbRepoService.requires(TeiidServiceNames.BUFFER_MGR);
        Supplier<ObjectReplicator> objectReplicatorDep = replicatorAvailable ? vdbRepoService.requires(TeiidServiceNames.OBJECT_REPLICATOR) : new Supplier<ObjectReplicator>() {
            @Override
            public ObjectReplicator get() {
                return null;
            }
        };
        Consumer<VDBRepository> vdbRepoConsumer = vdbRepoService.provides(TeiidServiceNames.VDB_REPO);
        VDBRepositoryService vdbRepositoryService = new VDBRepositoryService(vdbRepository, bufferManagerDep, objectReplicatorDep, vdbRepoConsumer);
        vdbRepoService.setInstance(vdbRepositoryService);
        vdbRepoService.install();

        // TODO: remove verbose service by moving the buffer service from runtime project
        RelativePathService.addService(TeiidServiceNames.BUFFER_DIR, "teiid-buffer", "jboss.server.temp.dir", target); //$NON-NLS-1$ //$NON-NLS-2$
        ServiceBuilder<?> bufferServiceBuilder = target.addService(TeiidServiceNames.BUFFER_MGR);
        Consumer<BufferManager> provides = bufferServiceBuilder.provides(TeiidServiceNames.BUFFER_MGR);
        Supplier<String> pathSupplier = bufferServiceBuilder.requires(TeiidServiceNames.BUFFER_DIR);
        BufferManagerService bufferService = buildBufferManager(context, operation, pathSupplier, provides);
        bufferServiceBuilder.setInstance(bufferService);
        bufferServiceBuilder.install();

        ServiceBuilder<?> tupleBufferBuilder = target.addService(TeiidServiceNames.TUPLE_BUFFER);
        Supplier<BufferManager> bmDep = tupleBufferBuilder.requires(TeiidServiceNames.BUFFER_MGR);
        Consumer<TupleBufferCache> tupleBufferSupplier = tupleBufferBuilder.provides(TeiidServiceNames.TUPLE_BUFFER);
        TupleBufferCacheService tupleBufferService = new TupleBufferCacheService(bmDep, tupleBufferSupplier);
        if(replicatorAvailable) {
            final ServiceController<JGroupsObjectReplicator> orcCopy = (ServiceController<JGroupsObjectReplicator>) objectReplicatorController;
            tupleBufferService.replicatorInjector = new Supplier<ObjectReplicator>() {
                @Override
                public ObjectReplicator get() {
                    return orcCopy.getValue();
                }
            };
        }
        tupleBufferBuilder.setInstance(tupleBufferService);
        tupleBufferBuilder.install();

        PolicyDecider policyDecider = null;
        if (isDefined(POLICY_DECIDER_MODULE_ELEMENT, operation, context)) {
            policyDecider = buildService(PolicyDecider.class, asString(POLICY_DECIDER_MODULE_ELEMENT, operation, context));
        }

        final AuthorizationValidator authValidator;
        if (isDefined(AUTHORIZATION_VALIDATOR_MODULE_ELEMENT, operation, context)) {
            authValidator = buildService(AuthorizationValidator.class, asString(AUTHORIZATION_VALIDATOR_MODULE_ELEMENT, operation, context));
        }
        else {
            DefaultAuthorizationValidator dap = new DefaultAuthorizationValidator();
            dap.setPolicyDecider(policyDecider);
            authValidator = dap;
        }

        ServiceBuilder<?> authValidatorService = target.addService(TeiidServiceNames.AUTHORIZATION_VALIDATOR);
        Consumer<AuthorizationValidator> authValidatorConsumer = authValidatorService.provides(TeiidServiceNames.AUTHORIZATION_VALIDATOR);
        authValidatorService.setInstance(Service.newInstance(authValidatorConsumer, authValidator));
        authValidatorService.install();

        final PreParser preParser;
        if (isDefined(PREPARSER_MODULE_ELEMENT, operation, context)) {
            preParser = buildService(PreParser.class, asString(PREPARSER_MODULE_ELEMENT, operation, context));
        } else {
            preParser = new PreParser() {

                @Override
                public String preParse(String command, CommandContext context) {
                    return command;
                }
            };
        }

        ServiceBuilder<?> preParserService = target.addService(TeiidServiceNames.PREPARSER);
        Consumer<PreParser> preParserConsumer = preParserService.provides(TeiidServiceNames.PREPARSER);
        preParserService.setInstance(Service.newInstance(preParserConsumer, preParser));
        preParserService.install();

        // resultset cache
        boolean rsCache = true;
        if (isDefined(RSC_ENABLE_ATTRIBUTE, operation, context) && !asBoolean(RSC_ENABLE_ATTRIBUTE, operation, context)) {
            rsCache = false;
        }

        if (!isDefined(RSC_CONTAINER_NAME_ATTRIBUTE, operation, context)) {
            throw new OperationFailedException(IntegrationPlugin.Util.gs(IntegrationPlugin.Event.TEIID50094));
        }

        String cacheName = "resultset"; //$NON-NLS-1$
        if (isDefined(RSC_NAME_ATTRIBUTE, operation, context)) {
            // if null; default cache will be used
            cacheName = asString(RSC_NAME_ATTRIBUTE, operation, context);
        }

        if (rsCache) {
            ServiceBuilder<?> cacheFactoryBuilder = target.addService(TeiidServiceNames.RESULTSET_CACHE_FACTORY);
            Consumer<CacheFactory> cfCons = cacheFactoryBuilder.provides(TeiidServiceNames.RESULTSET_CACHE_FACTORY);
            String ispnName = asString(RSC_CONTAINER_NAME_ATTRIBUTE, operation, context);
            Supplier<EmbeddedCacheManager> cmDep = cacheFactoryBuilder.requires(InfinispanRequirement.CONTAINER.getServiceName(context, ispnName)); // $NON-NLS-1$
            CacheFactoryService cfs = new CacheFactoryService(cmDep, cfCons);
            cacheFactoryBuilder.setInstance(cfs);
            cacheFactoryBuilder.install();

            int maxStaleness = DQPConfiguration.DEFAULT_MAX_STALENESS_SECONDS;
            if (isDefined(RSC_MAX_STALENESS_ATTRIBUTE, operation, context)) {
                maxStaleness = asInt(RSC_MAX_STALENESS_ATTRIBUTE, operation, context);
            }

            ServiceBuilder<?> resultsCacheBuilder = target.addService(TeiidServiceNames.CACHE_RESULTSET);
            Consumer<SessionAwareCache<CachedResults>> sacConsumer = resultsCacheBuilder.provides(TeiidServiceNames.CACHE_RESULTSET);
            Supplier<TupleBufferCache> tbcDep = resultsCacheBuilder.requires(TeiidServiceNames.TUPLE_BUFFER);
            Supplier<CacheFactory> cfDep = resultsCacheBuilder.requires(TeiidServiceNames.RESULTSET_CACHE_FACTORY);
            resultsCacheBuilder.requires(InfinispanCacheRequirement.CACHE.getServiceName(context, ispnName, cacheName)); //$NON-NLS-1$
            resultsCacheBuilder.requires(InfinispanCacheRequirement.CACHE.getServiceName(context, ispnName, cacheName+SessionAwareCache.REPL)); //$NON-NLS-1$
            CacheService<CachedResults> resultSetService = new CacheService<CachedResults>(cacheName, SessionAwareCache.Type.RESULTSET, maxStaleness, tbcDep, cfDep, sacConsumer);
            resultsCacheBuilder.setInstance(resultSetService);
            resultsCacheBuilder.install();


        }

        // prepared-plan cache
        boolean ppCache = true;
        if (isDefined(PPC_ENABLE_ATTRIBUTE, operation, context)) {
            ppCache = asBoolean(PPC_ENABLE_ATTRIBUTE, operation, context);
        }

        if (!isDefined(PPC_CONTAINER_NAME_ATTRIBUTE, operation, context)) {
            throw new OperationFailedException(IntegrationPlugin.Util.gs(IntegrationPlugin.Event.TEIID50095));
        }

        cacheName = "preparedplan"; //$NON-NLS-1$
        if (isDefined(PPC_NAME_ATTRIBUTE, operation, context)) {
            cacheName = asString(PPC_NAME_ATTRIBUTE, operation, context);
        }

        if (ppCache) {
            ServiceBuilder<?> cacheFactoryBuilder = target.addService(TeiidServiceNames.PREPAREDPLAN_CACHE_FACTORY);
            Consumer<CacheFactory> ppcfCons = cacheFactoryBuilder.provides(TeiidServiceNames.PREPAREDPLAN_CACHE_FACTORY);
            String ispnName = asString(PPC_CONTAINER_NAME_ATTRIBUTE, operation, context);
            Supplier<EmbeddedCacheManager> ecmDep = cacheFactoryBuilder.requires(InfinispanRequirement.CONTAINER.getServiceName(context, ispnName)); // $NON-NLS-1$
            CacheFactoryService cfs = new CacheFactoryService(ecmDep, ppcfCons);
            cacheFactoryBuilder.setInstance(cfs);
            cacheFactoryBuilder.install();

            ServiceBuilder<?> preparedPlanCacheBuilder = target.addService(TeiidServiceNames.CACHE_PREPAREDPLAN);
            Consumer<SessionAwareCache<PreparedPlan>> ppConsumer = preparedPlanCacheBuilder.provides(TeiidServiceNames.CACHE_PREPAREDPLAN);
            Supplier<CacheFactory> ppcDep = preparedPlanCacheBuilder.requires(TeiidServiceNames.PREPAREDPLAN_CACHE_FACTORY);
            preparedPlanCacheBuilder.requires(InfinispanCacheRequirement.CACHE.getServiceName(context, ispnName, cacheName)); // $NON-NLS-1$
            CacheService<PreparedPlan> preparedPlanService = new CacheService<>(cacheName, SessionAwareCache.Type.PREPAREDPLAN, 0, null, ppcDep, ppConsumer);
            preparedPlanCacheBuilder.setInstance(preparedPlanService);
            preparedPlanCacheBuilder.install();
        }

        // Query Engine
        ServiceBuilder<?> edfsServiceBuilder = target.addService(TeiidServiceNames.EVENT_DISTRIBUTOR_FACTORY);
        Consumer<InternalEventDistributorFactory> factoryConsumer = edfsServiceBuilder.provides(TeiidServiceNames.EVENT_DISTRIBUTOR_FACTORY);
        Supplier<VDBRepository> vrDep = edfsServiceBuilder.requires(TeiidServiceNames.VDB_REPO);
        Supplier<ObjectReplicator> orepDep = replicatorAvailable ? edfsServiceBuilder.requires(TeiidServiceNames.OBJECT_REPLICATOR) : new Supplier<ObjectReplicator>() {
            @Override
            public ObjectReplicator get() {
                return null;
            }
        };
        EventDistributorFactoryService edfs = new EventDistributorFactoryService(vrDep, orepDep, factoryConsumer);
        edfsServiceBuilder.setInstance(edfs);
        edfsServiceBuilder.install();

        String workManager = "default"; //$NON-NLS-1$
        if (isDefined(WORKMANAGER, operation, context)) {
            workManager = asString(WORKMANAGER, operation, context);
        }

        ServiceBuilder<?> engineBuilder = target.addService(TeiidServiceNames.ENGINE);
        Consumer<DQPCore> dqpConsumer = engineBuilder.provides(TeiidServiceNames.ENGINE);
        Supplier<WorkManager> workManagerDep = engineBuilder.requires(ServiceName.JBOSS.append("connector", "workmanager", workManager)); //$NON-NLS-1$ //$NON-NLS-2$
        Supplier<XATerminator> xatDep = engineBuilder.requires(ServiceName.JBOSS.append("txn", "XATerminator")); //$NON-NLS-1$ //$NON-NLS-2$
        Supplier<TransactionManager> tmDep = engineBuilder.requires(ServiceName.JBOSS.append("txn", "TransactionManager")); //$NON-NLS-1$ //$NON-NLS-2$
        Supplier<BufferManager> bufmanDep = engineBuilder.requires(TeiidServiceNames.BUFFER_MGR);
        Supplier<TranslatorRepository> transRepDep = engineBuilder.requires(TeiidServiceNames.TRANSLATOR_REPO);
        Supplier<VDBRepository> vdbRepDep = engineBuilder.requires(TeiidServiceNames.VDB_REPO);
        Supplier<AuthorizationValidator> authValdep = engineBuilder.requires(TeiidServiceNames.AUTHORIZATION_VALIDATOR);
        Supplier<PreParser> preParDep = engineBuilder.requires(TeiidServiceNames.PREPARSER);
        Supplier<SessionAwareCache<CachedResults>> resultsetDep = engineBuilder.requires(TeiidServiceNames.CACHE_RESULTSET);
        Supplier<SessionAwareCache<PreparedPlan>> pplanDep = engineBuilder.requires(TeiidServiceNames.CACHE_PREPAREDPLAN);
        Supplier<InternalEventDistributorFactory> evtDistDep = engineBuilder.requires(TeiidServiceNames.EVENT_DISTRIBUTOR_FACTORY);
        engineBuilder.setInitialMode(ServiceController.Mode.ACTIVE);
        final DQPCoreService engine = buildQueryEngine(context, operation, workManagerDep, xatDep, tmDep, bufmanDep, transRepDep, vdbRepDep, authValdep, preParDep, resultsetDep, pplanDep, evtDistDep, dqpConsumer);
        edfs.dqpCore = engine.getValue();
        engineBuilder.setInstance(engine);
        engineBuilder.install();

        // add JNDI for event distributor
        final ServiceName referenceFactoryServiceName = TeiidServiceNames.EVENT_DISTRIBUTOR_FACTORY.append("reference-factory"); //$NON-NLS-1$
        final ServiceBuilder<?> referenceBuilder = target.addService(referenceFactoryServiceName);
        Consumer<ManagedReference> referenceConsumer = referenceBuilder.provides(referenceFactoryServiceName);
        Supplier<EventDistributorFactory> edfDep = referenceBuilder.requires(TeiidServiceNames.EVENT_DISTRIBUTOR_FACTORY);
        referenceBuilder.setInitialMode(ServiceController.Mode.ACTIVE);
        final ReferenceFactoryService<EventDistributorFactory> referenceFactoryService = new ReferenceFactoryService<>(edfDep, referenceConsumer);
        referenceBuilder.setInstance(referenceFactoryService);

        String jndiName = "teiid/event-distributor-factory";//$NON-NLS-1$
        final ContextNames.BindInfo bindInfo = ContextNames.bindInfoFor(jndiName);
        final BinderService binderService = new BinderService(bindInfo.getBindName());
        final ServiceBuilder<?> binderBuilder = target.addService(bindInfo.getBinderServiceName(), binderService);
        binderBuilder.addDependency(referenceFactoryServiceName, ManagedReferenceFactory.class, binderService.getManagedObjectInjector());
        binderBuilder.addDependency(bindInfo.getParentContextServiceName(), ServiceBasedNamingStore.class, binderService.getNamingStoreInjector());
        binderBuilder.setInitialMode(ServiceController.Mode.ACTIVE);

        referenceBuilder.install();
        binderBuilder.install();

        LogManager.logDetail(LogConstants.CTX_RUNTIME, IntegrationPlugin.Util.getString("event_distributor_bound", jndiName)); //$NON-NLS-1$

        // Materialization management service
        ServiceBuilder<?> matviewBuilder = target.addService(TeiidServiceNames.MATVIEW_SERVICE);
        Consumer<MaterializationManager> matviewCons = matviewBuilder.provides(TeiidServiceNames.MATVIEW_SERVICE);
        Supplier<DQPCore> dqpCoreDep = matviewBuilder.requires(TeiidServiceNames.ENGINE);
        Supplier<VDBRepository> vdbDep = matviewBuilder.requires(TeiidServiceNames.VDB_REPO);
        Supplier<NodeTracker> nodeTrackerDep = replicatorAvailable ? matviewBuilder.requires(TeiidServiceNames.NODE_TRACKER_SERVICE) : new Supplier<NodeTracker>() {
            @Override
            public NodeTracker get() {
                return null;
            }
        };
        MaterializationManagementService matviewService = new MaterializationManagementService(shutdownListener, scheduler, dqpCoreDep, vdbDep, nodeTrackerDep, matviewCons);
        matviewBuilder.setInstance(matviewService);
        matviewBuilder.install();

        // Register VDB deployer
        context.addStep(new AbstractDeploymentChainStep() {
            @Override
            public void execute(DeploymentProcessorTarget processorTarget) {
                // vdb deployers
                processorTarget.addDeploymentProcessor(TeiidExtension.TEIID_SUBSYSTEM, Phase.STRUCTURE, 0, new FileRootMountProcessor(".ddl"));
                processorTarget.addDeploymentProcessor(TeiidExtension.TEIID_SUBSYSTEM, Phase.STRUCTURE, Phase.STRUCTURE_WAR_DEPLOYMENT_INIT|0xFF75,new DynamicVDBRootMountDeployer());
                processorTarget.addDeploymentProcessor(TeiidExtension.TEIID_SUBSYSTEM, Phase.STRUCTURE, Phase.STRUCTURE_WAR_DEPLOYMENT_INIT|0xFF76,new VDBStructureDeployer());
                processorTarget.addDeploymentProcessor(TeiidExtension.TEIID_SUBSYSTEM, Phase.PARSE, Phase.PARSE_WEB_DEPLOYMENT|0x0001, new VDBParserDeployer(vdbRepository));
                processorTarget.addDeploymentProcessor(TeiidExtension.TEIID_SUBSYSTEM, Phase.DEPENDENCIES, Phase.DEPENDENCIES_WAR_MODULE|0x0001, new VDBDependencyDeployer());
                processorTarget.addDeploymentProcessor(TeiidExtension.TEIID_SUBSYSTEM, Phase.INSTALL, Phase.INSTALL_WAR_DEPLOYMENT|0x1000, new VDBDeployer(translatorRepo, vdbRepository, shutdownListener));

                // translator deployers
                processorTarget.addDeploymentProcessor(TeiidExtension.TEIID_SUBSYSTEM, Phase.STRUCTURE, Phase.STRUCTURE_JDBC_DRIVER|0x0001,new TranslatorStructureDeployer());
                processorTarget.addDeploymentProcessor(TeiidExtension.TEIID_SUBSYSTEM, Phase.DEPENDENCIES, Phase.DEPENDENCIES_MODULE|0x0001, new TranslatorDependencyDeployer());
                processorTarget.addDeploymentProcessor(TeiidExtension.TEIID_SUBSYSTEM, Phase.INSTALL, Phase.INSTALL_JDBC_DRIVER|0x0001, new TranslatorDeployer());
            }

        }, OperationContext.Stage.RUNTIME);

        //install a session service
        //TODO: may eventually couple this with DQPCore
        final SessionServiceImpl sessionServiceImpl = new SessionServiceImpl();
        bufferService.setSessionService(sessionServiceImpl);
        edfs.dqpCore.setSessionService(sessionServiceImpl);

        if (isDefined(AUTHENTICATION_SECURITY_DOMAIN_ATTRIBUTE, operation, context)) {
            String securityDomain = asString(AUTHENTICATION_SECURITY_DOMAIN_ATTRIBUTE, operation, context);
            sessionServiceImpl.setSecurityDomain(securityDomain);
/* The security domain is not yet ready, will retrieve it as needed
            SecurityDomain securityDomainX = JBossSecurityHelper.getSecurityDomain(securityDomain);
            if (securityDomainX != null) {
                securityDomainX.registerWithClassLoader(this.getClass().getClassLoader());
            } else {
                LogManager.logError(LogConstants.CTX_RUNTIME,"Security Domain '"+securityDomain+"' was not found");
            }
*/
        }

           if (isDefined(AUTHENTICATION_MAX_SESSIONS_ALLOWED_ATTRIBUTE, operation, context)) {
               sessionServiceImpl.setSessionMaxLimit(asLong(AUTHENTICATION_MAX_SESSIONS_ALLOWED_ATTRIBUTE, operation, context));
           }

           if (isDefined(AUTHENTICATION_SESSION_EXPIRATION_TIME_LIMIT_ATTRIBUTE, operation, context)) {
               sessionServiceImpl.setSessionExpirationTimeLimit(asLong(AUTHENTICATION_SESSION_EXPIRATION_TIME_LIMIT_ATTRIBUTE, operation, context));
           }

           if (isDefined(AUTHENTICATION_TYPE_ATTRIBUTE, operation, context)) {
               sessionServiceImpl.setAuthenticationType(AuthenticationType.valueOf(asString(AUTHENTICATION_TYPE_ATTRIBUTE, operation, context)));
           }
           else {
               sessionServiceImpl.setAuthenticationType(AuthenticationType.USERPASSWORD);
           }

           if (isDefined(AUTHENTICATION_TRUST_ALL_LOCAL_ATTRIBUTE, operation, context)) {
               boolean allowUnauthenticated = asBoolean(AUTHENTICATION_TRUST_ALL_LOCAL_ATTRIBUTE, operation, context);
               sessionServiceImpl.setTrustAllLocal(allowUnauthenticated);
        }

           sessionServiceImpl.setDqp(engine.getValue());
           sessionServiceImpl.setVDBRepository(vdbRepository);
           sessionServiceImpl.setSecurityHelper(new JBossSecurityHelper());
           sessionServiceImpl.start();

        ServiceBuilder<?> sessionServiceBuilder = target.addService(TeiidServiceNames.SESSION);
        Consumer<SessionService> sessionServiceConsumer = sessionServiceBuilder.provides(TeiidServiceNames.SESSION);
        ContainerSessionService containerSessionService = new ContainerSessionService(sessionServiceImpl, sessionServiceConsumer);
        sessionServiceBuilder.setInstance(containerSessionService);
           sessionServiceBuilder.install();

          // rest war service
           RestWarGenerator warGenerator= TeiidAdd.buildService(RestWarGenerator.class, "org.jboss.teiid.rest-service");
        ServiceBuilder<?> warGeneratorSvc = target.addService(TeiidServiceNames.REST_WAR_SERVICE);
        ServiceName modelControllerClientFactory = context.getCapabilityServiceName("org.wildfly.management.model-controller-client-factory", ModelControllerClientFactory.class);
        Supplier<ModelControllerClientFactory> modelContDep = warGeneratorSvc.requires(modelControllerClientFactory);
        Supplier<Executor> exDep = warGeneratorSvc.requires(TeiidServiceNames.THREAD_POOL_SERVICE);
        Supplier<VDBRepository> repDep = warGeneratorSvc.requires(TeiidServiceNames.VDB_REPO);
        ResteasyEnabler restEnabler = new ResteasyEnabler(warGenerator, modelContDep, exDep, repDep);
        warGeneratorSvc.setInstance(restEnabler);
        warGeneratorSvc.install();
    }

    private ConnectorManagerRepository buildConnectorManagerRepository(final TranslatorRepository translatorRepo) {
        ConnectorManagerRepository cmr = new ConnectorManagerRepository();
        ConnectorManagerRepository.ExecutionFactoryProvider provider = new ConnectorManagerRepository.ExecutionFactoryProvider() {
            HashMap<String, ExecutionFactory<Object, Object>> map = new HashMap<String, ExecutionFactory<Object, Object>>();
            @Override
            public ExecutionFactory<Object, Object> getExecutionFactory(String name) throws ConnectorManagerException {
                VDBTranslatorMetaData translator = translatorRepo.getTranslatorMetaData(name);
                if (translator == null) {
                    throw new ConnectorManagerException(
                            IntegrationPlugin.Util.gs(IntegrationPlugin.Event.TEIID50110, name));
                }
                ExecutionFactory<Object, Object> ef = map.get(name);
                if ( ef == null) {
                    ef = TranslatorUtil.buildDelegateAwareExecutionFactory(translator, this);
                    map.put(name, ef);
                }
                return ef;
            }
        };
        cmr.setProvider(provider);
        return cmr;
    }

    private void buildThreadService(int maxThreads, ServiceTarget target) {
        final ServiceBuilder<?> serviceBuilder = target.addService(TeiidServiceNames.THREAD_POOL_SERVICE);
        Consumer<TeiidExecutor> provides = serviceBuilder.provides(TeiidServiceNames.THREAD_POOL_SERVICE);
        ThreadExecutorService service = new ThreadExecutorService(maxThreads, provides);
        serviceBuilder.setInstance(service);
        serviceBuilder.install();
    }

    private static final class ContainerSessionService implements
            org.jboss.msc.service.Service<SessionService> {
        private final SessionServiceImpl sessionServiceImpl;
        private final Consumer<SessionService> serviceConsumer;

        private ContainerSessionService(SessionServiceImpl sessionServiceImpl, Consumer<SessionService> sessionServiceConsumer) {
            this.sessionServiceImpl = sessionServiceImpl;
            this.serviceConsumer = sessionServiceConsumer;
        }

        @Override
        public SessionService getValue() throws IllegalStateException,
                IllegalArgumentException {
            return sessionServiceImpl;
        }

        @Override
        public void stop(StopContext context) {
            sessionServiceImpl.stop();
        }

        @Override
        public void start(StartContext context) throws StartException {
            this.serviceConsumer.accept(sessionServiceImpl);
        }
    }

    /**
     * Load the service and wrap it with a proxy to associate the appropriate classloader.
     * @param type
     * @param moduleName
     * @return
     * @throws OperationFailedException
     */
    static <T> T buildService(Class<T> type, String moduleName) throws OperationFailedException {
        final T instance = loadService(type, moduleName, null);
        return wrapWithClassLoaderProxy(type, instance);
    }

    static <T> T wrapWithClassLoaderProxy(Class<T> type,
            final T instance) {
        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(instance.getClass().getClassLoader(), new Class[] { type }, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                ClassLoader originalCL = Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(instance.getClass().getClassLoader());
                    return method.invoke(instance, args);
                } catch (InvocationTargetException e) {
                    throw e.getTargetException();
                } finally {
                    Thread.currentThread().setContextClassLoader(originalCL);
                }
            }
        });
        return proxy;
    }

    static <T> T loadService(Class<T> type, String moduleName, ServiceModuleLoader sml)
            throws OperationFailedException {
        Module module = null;
        try {
            module = Module.getModuleFromCallerModuleLoader(moduleName);
        } catch (ModuleLoadException e) {
            if (sml != null) {
                try {
                    module = sml.loadModule(moduleName);
                } catch (ModuleLoadException e1) {
                }
            }
            if (module == null) {
                throw new OperationFailedException(IntegrationPlugin.Util.gs(IntegrationPlugin.Event.TEIID50069, moduleName), e);
            }
        }
        ServiceLoader<T> services = module.loadService(type);
        Iterator<T> iter = services.iterator();
        if (!iter.hasNext()) {
            throw new OperationFailedException(IntegrationPlugin.Util.gs(IntegrationPlugin.Event.TEIID50089, type.getName(), moduleName));
        }
        return iter.next();
    }

    private BufferManagerService buildBufferManager(final OperationContext context, ModelNode node, Supplier<String> pathSupplier, Consumer<BufferManager> provides) throws OperationFailedException {
        BufferManagerService bufferManger = new BufferManagerService(pathSupplier, provides);

        if (node == null) {
            return bufferManger;
        }

        if (isDefined(BUFFER_MANAGER_USE_DISK_ATTRIBUTE, node, context)) {
            bufferManger.setUseDisk(asBoolean(BUFFER_MANAGER_USE_DISK_ATTRIBUTE, node, context));
        } else if (isDefined(USE_DISK_ATTRIBUTE, node, context)) {
            bufferManger.setUseDisk(asBoolean(USE_DISK_ATTRIBUTE, node, context));
        }
        if (isDefined(BUFFER_MANAGER_PROCESSOR_BATCH_SIZE_ATTRIBUTE, node, context)) {
            bufferManger.setProcessorBatchSize(asInt(BUFFER_MANAGER_PROCESSOR_BATCH_SIZE_ATTRIBUTE, node, context));
        } else if (isDefined(PROCESSOR_BATCH_SIZE_ATTRIBUTE, node, context)) {
            bufferManger.setProcessorBatchSize(asInt(PROCESSOR_BATCH_SIZE_ATTRIBUTE, node, context));
        }
        if (isDefined(BUFFER_MANAGER_MAX_PROCESSING_KB_ATTRIBUTE, node, context)) {
            bufferManger.setMaxProcessingKb(asInt(BUFFER_MANAGER_MAX_PROCESSING_KB_ATTRIBUTE, node, context));
        } else if (isDefined(MAX_PROCESSING_KB_ATTRIBUTE, node, context)) {
            bufferManger.setMaxProcessingKb(asInt(MAX_PROCESSING_KB_ATTRIBUTE, node, context));
        }
        if (isDefined(BUFFER_MANAGER_MAX_RESERVED_MB_ATTRIBUTE, node, context)) {
            bufferManger.setMaxReservedHeapMb(asInt(BUFFER_MANAGER_MAX_RESERVED_MB_ATTRIBUTE, node, context));
        } else if (isDefined(MAX_RESERVED_KB_ATTRIBUTE, node, context)) {
            bufferManger.setMaxReserveKb(asInt(MAX_RESERVED_KB_ATTRIBUTE, node, context));
        }
        if (isDefined(BUFFER_MANAGER_MAX_FILE_SIZE_ATTRIBUTE, node, context)) {
            bufferManger.setMaxFileSize(asLong(BUFFER_MANAGER_MAX_FILE_SIZE_ATTRIBUTE, node, context));
        } else if (isDefined(MAX_FILE_SIZE_ATTRIBUTE, node, context)) {
            bufferManger.setMaxFileSize(asLong(MAX_FILE_SIZE_ATTRIBUTE, node, context));
        }
        if (isDefined(BUFFER_MANAGER_MAX_BUFFER_SPACE_ATTRIBUTE, node, context)) {
            bufferManger.setMaxDiskBufferSpaceMb(asLong(BUFFER_MANAGER_MAX_BUFFER_SPACE_ATTRIBUTE, node, context));
        } else if (isDefined(MAX_BUFFER_SPACE_ATTRIBUTE, node, context)) {
            bufferManger.setMaxDiskBufferSpaceMb(asLong(MAX_BUFFER_SPACE_ATTRIBUTE, node, context));
        }
        if (isDefined(BUFFER_MANAGER_MAX_OPEN_FILES_ATTRIBUTE, node, context)) {
            bufferManger.setMaxOpenFiles(asInt(BUFFER_MANAGER_MAX_OPEN_FILES_ATTRIBUTE, node, context));
        } else  if (isDefined(MAX_OPEN_FILES_ATTRIBUTE, node, context)) {
            bufferManger.setMaxOpenFiles(asInt(MAX_OPEN_FILES_ATTRIBUTE, node, context));
        }
        if (isDefined(BUFFER_MANAGER_MEMORY_BUFFER_SPACE_ATTRIBUTE, node, context)) {
            bufferManger.setFixedMemoryBufferSpaceMb(asInt(BUFFER_MANAGER_MEMORY_BUFFER_SPACE_ATTRIBUTE, node, context));
        } else if (isDefined(MEMORY_BUFFER_SPACE_ATTRIBUTE, node, context)) {
            bufferManger.setFixedMemoryBufferSpaceMb(asInt(MEMORY_BUFFER_SPACE_ATTRIBUTE, node, context));
        }
        if (isDefined(BUFFER_MANAGER_MEMORY_BUFFER_OFFHEAP_ATTRIBUTE, node, context)) {
            bufferManger.setFixedMemoryBufferOffHeap(asBoolean(BUFFER_MANAGER_MEMORY_BUFFER_OFFHEAP_ATTRIBUTE, node, context));
        } else if (isDefined(MEMORY_BUFFER_OFFHEAP_ATTRIBUTE, node, context)) {
            bufferManger.setFixedMemoryBufferOffHeap(asBoolean(MEMORY_BUFFER_OFFHEAP_ATTRIBUTE, node, context));
        }
        if (isDefined(BUFFER_MANAGER_MAX_STORAGE_OBJECT_SIZE_ATTRIBUTE, node, context)) {
            bufferManger.setMaxStorageObjectSizeKb(asInt(BUFFER_MANAGER_MAX_STORAGE_OBJECT_SIZE_ATTRIBUTE, node, context));
        } else if (isDefined(MAX_STORAGE_OBJECT_SIZE_ATTRIBUTE, node, context)) {
            bufferManger.setMaxStorageObjectSize(asInt(MAX_STORAGE_OBJECT_SIZE_ATTRIBUTE, node, context));
        }
        if (isDefined(BUFFER_MANAGER_INLINE_LOBS, node, context)) {
            bufferManger.setInlineLobs(asBoolean(BUFFER_MANAGER_INLINE_LOBS, node, context));
        } else if (isDefined(INLINE_LOBS, node, context)) {
            bufferManger.setInlineLobs(asBoolean(INLINE_LOBS, node, context));
        }
        if (isDefined(BUFFER_MANAGER_ENCRYPT_FILES_ATTRIBUTE, node, context)) {
            bufferManger.setEncryptFiles(asBoolean(BUFFER_MANAGER_ENCRYPT_FILES_ATTRIBUTE, node, context));
        } else if (isDefined(ENCRYPT_FILES_ATTRIBUTE, node, context)) {
            bufferManger.setEncryptFiles(asBoolean(ENCRYPT_FILES_ATTRIBUTE, node, context));
        }
        return bufferManger;
    }

    private DQPCoreService buildQueryEngine(final OperationContext context, ModelNode node, Supplier<WorkManager> workManagerDep, Supplier<XATerminator> xatDep, Supplier<TransactionManager> tmDep, Supplier<BufferManager> bufmanDep, Supplier<TranslatorRepository> transRepDep, Supplier<VDBRepository> vdbRepDep, Supplier<AuthorizationValidator> authValdep, Supplier<PreParser> preParDep, Supplier<SessionAwareCache<CachedResults>> resultsetDep, Supplier<SessionAwareCache<PreparedPlan>> pplanDep, Supplier<InternalEventDistributorFactory> evtDistDep, Consumer<DQPCore> dqpConsumer) throws OperationFailedException {
        DQPCoreService engine = new DQPCoreService(workManagerDep, xatDep, tmDep, bufmanDep, transRepDep, vdbRepDep, authValdep, preParDep, resultsetDep, pplanDep, evtDistDep, dqpConsumer);

        if (isDefined(MAX_THREADS_ELEMENT, node, context)) {
            engine.setMaxThreads(asInt(MAX_THREADS_ELEMENT, node, context));
        }
        if (isDefined(MAX_ACTIVE_PLANS_ELEMENT, node, context)) {
            engine.setMaxActivePlans(asInt(MAX_ACTIVE_PLANS_ELEMENT, node, context));
        }
        if (isDefined(USER_REQUEST_SOURCE_CONCURRENCY_ELEMENT, node, context)) {
            engine.setUserRequestSourceConcurrency(asInt(USER_REQUEST_SOURCE_CONCURRENCY_ELEMENT, node, context));
        }
        if (isDefined(TIME_SLICE_IN_MILLI_ELEMENT, node, context)) {
            engine.setTimeSliceInMilli(asInt(TIME_SLICE_IN_MILLI_ELEMENT, node, context));
        }
        if (isDefined(MAX_ROWS_FETCH_SIZE_ELEMENT, node, context)) {
            engine.setMaxRowsFetchSize(asInt(MAX_ROWS_FETCH_SIZE_ELEMENT, node, context));
        }
        if (isDefined(LOB_CHUNK_SIZE_IN_KB_ELEMENT, node, context)) {
            engine.setLobChunkSizeInKB(asInt(LOB_CHUNK_SIZE_IN_KB_ELEMENT, node, context));
        }
        if (isDefined(QUERY_THRESHOLD_IN_SECS_ELEMENT, node, context)) {
            engine.setQueryThresholdInSecs(asInt(QUERY_THRESHOLD_IN_SECS_ELEMENT, node, context));
        }
        if (isDefined(MAX_SOURCE_ROWS_ELEMENT, node, context)) {
            engine.setMaxSourceRows(asInt(MAX_SOURCE_ROWS_ELEMENT, node, context));
        }
        if (isDefined(EXCEPTION_ON_MAX_SOURCE_ROWS_ELEMENT, node, context)) {
            engine.setExceptionOnMaxSourceRows(asBoolean(EXCEPTION_ON_MAX_SOURCE_ROWS_ELEMENT, node, context));
        }
        if (isDefined(DETECTING_CHANGE_EVENTS_ELEMENT, node, context)) {
            engine.setDetectingChangeEvents(asBoolean(DETECTING_CHANGE_EVENTS_ELEMENT, node, context));
        }
        if (isDefined(QUERY_TIMEOUT, node, context)) {
            engine.setQueryTimeout(asLong(QUERY_TIMEOUT, node, context));
        }
        return engine;
    }

    static class VDBStatusCheckerExecutorService extends VDBStatusChecker{
        final Supplier<Executor> executorInjector;
        final Supplier<VDBRepository> vdbRepoInjector;

        public VDBStatusCheckerExecutorService(Supplier<Executor> executorDep, Supplier<VDBRepository> vdbRepoDep) {
            this.executorInjector = executorDep;
            this.vdbRepoInjector = vdbRepoDep;
        }

        @Override
        public Executor getExecutor() {
            return this.executorInjector.get();
        }

        @Override
        public VDBRepository getVDBRepository() {
            return this.vdbRepoInjector.get();
        }
    }

    private void deployResources(OperationContext context) throws OperationFailedException{
        if (requiresRuntime(context)) {
            try {
                Module module = Module.forClass(getClass());
                if (module == null) {
                    return; // during testing
                }

                URL deployments = module.getExportedResource("deployments.properties"); //$NON-NLS-1$
                if (deployments == null) {
                    return; // no deployments
                }
                BufferedReader in = new BufferedReader(new InputStreamReader(deployments.openStream()));

                String deployment;
                while ((deployment = in.readLine()) != null) {
                    PathAddress deploymentAddress = PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT, deployment));
                    ModelNode op = new ModelNode();
                    op.get(OP).set(ADD);
                    op.get(OP_ADDR).set(deploymentAddress.toModelNode());
                    op.get(ENABLED).set(true);
                    op.get(PERSISTENT).set(false); // prevents writing this deployment out to standalone.xml

                    URL url = module.getExportedResource(deployment);
                    String urlString = url.toExternalForm();

                    ModelNode contentItem = new ModelNode();
                    contentItem.get(URL).set(urlString);
                    op.get(CONTENT).add(contentItem);

                    ImmutableManagementResourceRegistration rootResourceRegistration = context.getRootResourceRegistration();
                    OperationStepHandler handler = rootResourceRegistration.getOperationHandler(deploymentAddress, ADD);

                    context.addStep(op, handler, OperationContext.Stage.MODEL);
                }
                in.close();
            }catch(IOException e) {
                throw new OperationFailedException(e.getMessage(), e);
            }
        }
    }
}
