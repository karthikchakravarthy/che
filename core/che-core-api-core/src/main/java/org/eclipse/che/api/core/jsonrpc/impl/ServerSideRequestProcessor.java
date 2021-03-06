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
package org.eclipse.che.api.core.jsonrpc.impl;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.core.jsonrpc.commons.RequestProcessor;
import org.eclipse.che.commons.lang.concurrent.LoggingUncaughtExceptionHandler;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;

@Singleton
public class ServerSideRequestProcessor implements RequestProcessor {
    private ExecutorService executorService;

    @PostConstruct
    private void postConstruct(){
        ThreadFactory factory = new ThreadFactoryBuilder().setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.getInstance())
                                                          .setNameFormat(ServerSideRequestProcessor.class.getSimpleName())
                                                          .setDaemon(true)
                                                          .build();

        executorService = newCachedThreadPool(factory);
    }

    @PreDestroy
    private void preDestroy() {
        executorService.shutdown();
        try {
            if (executorService.awaitTermination(5, SECONDS)) {
                executorService.shutdownNow();
                executorService.awaitTermination(5, SECONDS);
            }
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void process(Runnable runnable) {
        executorService.execute(runnable);
    }
}
