/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.workspace.infrastructure.docker;

import com.google.gson.Gson;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.che.api.core.model.workspace.runtime.BootstrapperStatus;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.core.util.LineConsumer;
import org.eclipse.che.api.core.util.ProcessUtil;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.InternalMachineConfig;
import org.eclipse.che.api.workspace.shared.dto.RuntimeIdentityDto;
import org.eclipse.che.api.workspace.shared.dto.event.BootstrapperStatusEvent;
import org.eclipse.che.plugin.docker.client.LogMessage;
import org.eclipse.che.plugin.docker.client.MessageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Sergii Leshchenko
 */
public class Bootstrapper {
    private static final Logger LOG = LoggerFactory.getLogger(Bootstrapper.class);

    private final    EventService                              eventService;
    private final    String                                    machineName;
    private final    RuntimeIdentity                           runtimeIdentity;
    private final    DockerMachine                             dockerMachine;
    private final    String                                    apiEndpoint;
    private final    List<InternalMachineConfig.ResolvedAgent> agents;
    private final    CountDownLatch                            latch;
    private          String                                    machineType;
    private volatile BootstrapperStatusEvent                   resultEvent;

    @Inject
    public Bootstrapper(@Assisted String machineName,
                        @Assisted RuntimeIdentity runtimeIdentity,
                        @Assisted DockerMachine dockerMachine,
                        @Assisted List<InternalMachineConfig.ResolvedAgent> agents,
                        EventService eventService,
                        @Named("che.api") String apiEndpoint) {
        this.eventService = eventService;
        this.machineName = machineName;
        this.runtimeIdentity = runtimeIdentity;
        this.dockerMachine = dockerMachine;
        this.agents = agents;
        this.apiEndpoint = apiEndpoint;
        this.latch = new CountDownLatch(1);
    }

    public void bootstrap() throws InfrastructureException {
        this.eventService.subscribe(new EventSubscriber<BootstrapperStatusEvent>() {
            @Override
            public void onEvent(BootstrapperStatusEvent event) {
                RuntimeIdentityDto runtimeId = event.getRuntimeId();
                if ((event.getStatus().equals(BootstrapperStatus.DONE) ||
                     event.getStatus().equals(BootstrapperStatus.FAILED))
                    && event.getMachineName().equals(machineName)
                    && runtimeIdentity.getEnvName().equals(runtimeId.getEnvName())
                    && runtimeIdentity.getOwner().equals(runtimeId.getOwner())
                    && runtimeIdentity.getWorkspaceId().equals(runtimeId.getWorkspaceId())) {

                    resultEvent = event;
                    latch.countDown();

                    eventService.unsubscribe(this, BootstrapperStatusEvent.class);
                }
            }
        });

        bootstrapAsync();

        try {
            if (latch.await(10, TimeUnit.MINUTES)) {//TODO Configure
                if (resultEvent.getStatus().equals(BootstrapperStatus.FAILED)) {
                    throw new InfrastructureException(resultEvent.getError());
                }
            } else {
                throw new InfrastructureException("Timeout reached!");
            }
        } catch (InterruptedException e) {
            throw new InfrastructureException("Bootstrapping interrupted");//TODO
        }
    }

    private void bootstrapAsync() {
        Thread thread = new Thread(new Task());
        thread.setDaemon(true);
        thread.start();
    }

    class Task implements Runnable {
        @Override
        public void run() {
//            try {
//                dockerMachine.exec("uname -m", message -> {
//                    if (message.getType().equals(LogMessage.Type.STDOUT)) {
//                        machineType = message.getContent();
//                    }
//                });
//            } catch (InfrastructureException e) {
//                TODO
//            }


//            cp("bootstrapper/bootstrapper", "/home/user/bootstrapper/bootstrapper");
//
//            try {
//                Path config = Files.createTempFile("config", ".json");
//                Files.write(config, new Gson().toJson(agents).getBytes());
//                cp(config.toAbsolutePath().toString(), "/home/user/bootstrapper/config.json");
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }

            try {
                dockerMachine.putResource("/home/user",
                                          new ByteArrayInputStream(new Gson().toJson(agents).getBytes()));
            } catch (InfrastructureException e) {
                throw new RuntimeException(e);
            }

            try {
                dockerMachine.putResource("/home/user/bootstrapper/bootstrapper", Thread.currentThread()
                                                                                  .getContextClassLoader()
                                                                                  .getResourceAsStream("bootstrapper/bootstrapper"));
            } catch (InfrastructureException e) {
                throw new RuntimeException(e);
            }


            String wsEndpoint = apiEndpoint.replace("http", "ws") + "/installer/websocket/1";
            try {
                dockerMachine.exec("/home/user/bootstrapper/bootstrapper " +
                                   "-machine-name " + machineName + " " +
                                   "-runtime-id " + String.format("%s:%s:%s", runtimeIdentity.getWorkspaceId(),
                                                                  runtimeIdentity.getEnvName(),
                                                                  runtimeIdentity.getOwner()) + " " +
                                   "-push-endpoint " + wsEndpoint + " ", new MessageProcessor<LogMessage>() {
                    @Override
                    public void process(LogMessage message) {
                        LOG.info(message.getType() + " >> " + message.getContent());
                    }
                });
            } catch (InfrastructureException e) {
                throw new RuntimeException(e);
            }
        }

        private void cp(String resource, String target) {
            URL resourceUrl = Thread.currentThread()
                                    .getContextClassLoader()
                                    .getResource(resource);

            String absolutePath;
            if (resourceUrl == null) {
                absolutePath = target;
            } else {
                    absolutePath= Paths.get(resource).toAbsolutePath().toString();
            }

            try {
                absolutePath = new File(resourceUrl.toURI()).getAbsolutePath();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            try {
                Process process =
                        ProcessUtil.execute(new ProcessBuilder("/bin/bash", "-c", "docker cp " + absolutePath  + " " + dockerMachine.getContainer() + ":" + target),
                                            new LineConsumer() {
                                                @Override
                                                public void writeLine(String line) throws IOException {
                                                    LOG.info("CP >>>>> " + line);
                                                }

                                                @Override
                                                public void close() throws IOException {

                                                }
                                            });
                process.waitFor();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
