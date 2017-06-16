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

import com.google.inject.assistedinject.Assisted;

import org.eclipse.che.api.core.model.workspace.runtime.BootstrapperStatus;
import org.eclipse.che.api.core.model.workspace.runtime.InstallerStatus;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.workspace.server.DtoConverter;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.api.workspace.server.spi.InternalMachineConfig;
import org.eclipse.che.api.workspace.shared.dto.RuntimeIdentityDto;
import org.eclipse.che.api.workspace.shared.dto.event.BootstrapperStatusEvent;
import org.eclipse.che.api.workspace.shared.dto.event.InstallerLogEvent;
import org.eclipse.che.api.workspace.shared.dto.event.InstallerStatusEvent;
import org.eclipse.che.dto.server.DtoFactory;

import javax.inject.Inject;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Sergii Leshchenko
 */
public class Bootstrapper {
    private final    EventService                              eventService;
    private final    String                                    machineName;
    private final    RuntimeIdentity                           runtimeIdentity;
    private final    DockerMachine                             dockerMachine;
    private final    List<InternalMachineConfig.ResolvedAgent> agents;
    private final    CountDownLatch                            latch;
    private volatile BootstrapperStatusEvent                   resultEvent;

    @Inject
    public Bootstrapper(@Assisted String machineName,
                        @Assisted RuntimeIdentity runtimeIdentity,
                        @Assisted DockerMachine dockerMachine,
                        @Assisted List<InternalMachineConfig.ResolvedAgent> agents,
                        EventService eventService) {
        this.eventService = eventService;
        this.machineName = machineName;
        this.runtimeIdentity = runtimeIdentity;
        this.dockerMachine = dockerMachine;
        this.agents = agents;
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
        Thread thread = new Thread(() ->
                                   {
                                       eventService.publish(DtoFactory.newDto(BootstrapperStatusEvent.class)
                                                                      .withMachineName(machineName)
                                                                      .withRuntimeId(
                                                                              DtoConverter.asDto(runtimeIdentity))
                                                                      .withStatus(BootstrapperStatus.AVAILABLE)
                                                                      .withTime(Calendar.getInstance().toString()));

                                       for (InternalMachineConfig.ResolvedAgent resolvedAgent : agents) {
                                           try {
                                               launchAgent(resolvedAgent);
                                           } catch (InfrastructureException e) {
                                               eventService.publish(DtoFactory.newDto(BootstrapperStatusEvent.class)
                                                                              .withMachineName(machineName)
                                                                              .withRuntimeId(DtoConverter
                                                                                                     .asDto(runtimeIdentity))
                                                                              .withStatus(BootstrapperStatus.FAILED)
                                                                              .withTime(
                                                                                      Calendar.getInstance().toString())
                                                                              .withError(e.getMessage()));
                                               return;
                                           }

                                           eventService.publish(DtoFactory.newDto(BootstrapperStatusEvent.class)
                                                                          .withMachineName(machineName)
                                                                          .withRuntimeId(
                                                                                  DtoConverter.asDto(runtimeIdentity))
                                                                          .withStatus(BootstrapperStatus.DONE)
                                                                          .withTime(Calendar.getInstance().toString()));
                                       }

                                   });
        thread.setDaemon(true);
        thread.start();
    }

    private void launchAgent(InternalMachineConfig.ResolvedAgent resolvedAgent) throws InfrastructureException {
        eventService.publish(DtoFactory.newDto(InstallerStatusEvent.class)
                                       .withStatus(InstallerStatus.STARTING)
                                       .withMachineName(machineName)
                                       .withRuntimeId(DtoConverter.asDto(runtimeIdentity))
                                       .withInstaller(resolvedAgent.getId())
                                       .withTime(Calendar.getInstance().toString()));

        eventService.publish(DtoFactory.newDto(InstallerStatusEvent.class)
                                       .withStatus(InstallerStatus.RUNNING)
                                       .withMachineName(machineName)
                                       .withRuntimeId(DtoConverter.asDto(runtimeIdentity))
                                       .withInstaller(resolvedAgent.getId())
                                       .withTime(Calendar.getInstance().toString()));

        Thread thread = new Thread(() -> {
            try {
                dockerMachine.exec(resolvedAgent.getScript(),
                                   message -> {
                                       InstallerLogEvent.Stream stream;
                                       switch (message.getType()) {
                                           case STDOUT:
                                               stream = InstallerLogEvent.Stream.STDOUT;
                                               break;
                                           case STDERR:
                                               stream = InstallerLogEvent.Stream.STDERR;
                                               break;
                                           default:
                                               // do nothing
                                               return;
                                       }
                                       eventService.publish(DtoFactory.newDto(InstallerLogEvent.class)
                                                                      .withStream(stream)
                                                                      .withText(message.getContent())
                                                                      .withMachineName(machineName)
                                                                      .withRuntimeId(
                                                                              DtoConverter.asDto(runtimeIdentity))
                                                                      .withInstaller(resolvedAgent.getId())
                                                                      .withTime(Calendar.getInstance().toString()));
                                   }
                );
            } catch (InfrastructureException e) {
                eventService.publish(DtoFactory.newDto(InstallerStatusEvent.class)
                                               .withStatus(InstallerStatus.FAILED)
                                               .withMachineName(machineName)
                                               .withRuntimeId(DtoConverter.asDto(runtimeIdentity))
                                               .withError(e.getMessage())
                                               .withInstaller(resolvedAgent.getId())
                                               .withTime(Calendar.getInstance().toString()));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
}
