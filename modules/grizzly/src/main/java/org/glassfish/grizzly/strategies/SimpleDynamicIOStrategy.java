/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.grizzly.strategies;

import java.io.IOException;
import java.util.concurrent.Executor;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.IOStrategy;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.utils.CurrentThreadExecutor;
import org.glassfish.grizzly.utils.WorkerThreadExecutor;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.ExecutorService;

/**
 * Simple dynamic strategy, which switches I/O processing strategies, basing
 * on statistics. This implementation takes in consideration number of
 * {@link SelectionKey}s, which were selected last time by {@link Selector}.
 *
 * <tt>SimpleDynamicIOStrategy</tt> is able to use 2 strategies underneath:
 * {@link SameThreadIOStrategy}, {@link WorkerThreadIOStrategy}.
 * And is able to switch between them basing on corresponding threshold
 * (threshold represents the number of selected {@link SelectionKey}s).
 *
 * So the strategy is getting applied following way:
 *
 * {@link SameThreadIOStrategy} --(worker-thread threshold)--> {@link WorkerThreadIOStrategy}.
 *
 * @author Alexey Stashok
 */
public final class SimpleDynamicIOStrategy implements IOStrategy {
    private final SameThreadIOStrategy sameThreadStrategy;
    private final WorkerThreadIOStrategy workerThreadStrategy;

    private static final int WORKER_THREAD_THRESHOLD = 1;

    public SimpleDynamicIOStrategy(final ExecutorService workerThreadPool) {
        this(new CurrentThreadExecutor(),
                new WorkerThreadExecutor(workerThreadPool));
    }

    protected SimpleDynamicIOStrategy(final Executor sameThreadProcessorExecutor,
                                      final Executor workerThreadProcessorExecutor) {
        sameThreadStrategy = new SameThreadIOStrategy();
        workerThreadStrategy = new WorkerThreadIOStrategy(
                sameThreadProcessorExecutor, workerThreadProcessorExecutor);
    }

    @Override
    public boolean executeIoEvent(Connection connection, IOEvent ioEvent)
            throws IOException {
        final NIOConnection nioConnection = (NIOConnection) connection;
        final int lastSelectedKeysCount = nioConnection.getSelectorRunner().getLastSelectedKeysCount();

        if (lastSelectedKeysCount <= WORKER_THREAD_THRESHOLD) {
            return sameThreadStrategy.executeIoEvent(connection, ioEvent);
        } else {
            return workerThreadStrategy.executeIoEvent(connection, ioEvent);
        }
    }

    public ThreadPoolConfig createDefaultWorkerPoolConfig(final NIOTransport transport) {

        final ThreadPoolConfig config = ThreadPoolConfig.defaultConfig().clone();
        final int selectorRunnerCount = transport.getSelectorRunnersCount();
        config.setCorePoolSize(selectorRunnerCount * 2);
        config.setMaxPoolSize(selectorRunnerCount * 2);
        config.setMemoryManager(transport.getMemoryManager());
        return config;

    }
}
